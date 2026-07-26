package com.group3.cinema.service.payment;

/*
 * Added on 2026-06-26: payOS/VietQR payment gateway integration for customer bookings.
 * Created by: HuyPB - HE191335
 */

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PayOsGatewayService implements PaymentGatewayService {
    // Gson TypeToken giữ generic Map khi deserialize JSON response.
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    // Giới hạn thời gian mở kết nối TCP/TLS.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    // Giới hạn tổng thời gian mỗi request payOS.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    // Gson chuyển payload Map ↔ JSON.
    private final Gson gson = new Gson();

    // HttpClient dùng lại kết nối và áp dụng connect timeout chung.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Value("${payment.payos.enabled:false}")
    private boolean enabled;

    @Value("${payment.payos.endpoint:https://api-merchant.payos.vn/v2/payment-requests}")
    private String endpoint;

    @Value("${payment.payos.client-id:}")
    private String clientId;

    @Value("${payment.payos.api-key:}")
    private String apiKey;

    @Value("${payment.payos.checksum-key:}")
    private String checksumKey;

    @Value("${payment.payos.return-url:}")
    private String returnUrl;

    @Value("${payment.payos.cancel-url:}")
    private String cancelUrl;

    @Override
    public Payment.Method method() {
        // Router dùng giá trị này để đăng ký service dưới key PAYOS.
        return Payment.Method.PAYOS;
    }

    @Override
    public boolean isConfigured() {
        // Chỉ sẵn sàng khi feature bật và toàn bộ endpoint/credential/callback URL có giá trị.
        return enabled && !endpoint.isBlank() && !clientId.isBlank() && !apiKey.isBlank()
                && !checksumKey.isBlank() && !returnUrl.isBlank() && !cancelUrl.isBlank();
    }

    @Override
    public String createPaymentUrl(Payment payment, Booking booking, HttpServletRequest request) {
        // payOS nhận số tiền nguyên VND nên bỏ phần thập phân.
        String amount = payment.getAmount().setScale(0, RoundingMode.DOWN).toPlainString();

        // Description bị giới hạn ký tự nên chỉ giữ chữ/số/space.
        String description = ("CF" + booking.getId() + " VE").replaceAll("[^A-Za-z0-9 ]", "");

        // API dùng Unix epoch seconds cho hạn thanh toán.
        long expiredAt = booking.getExpiresAt().atZone(ZoneId.systemDefault()).toEpochSecond();

        // Chuỗi ký phải đúng thứ tự field theo tài liệu payOS.
        String rawSignature = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + payment.getOrderCode()
                + "&returnUrl=" + returnUrl;

        // HMAC SHA-256 bằng checksum key chứng minh payload do merchant tạo.
        String signature = PaymentGatewayUtils.hmacSha256(rawSignature, checksumKey);

        // LinkedHashMap giữ thứ tự dễ đọc khi serialize/debug.
        Map<String, Object> body = new LinkedHashMap<>();

        // orderCode và amount phải là number trong JSON.
        body.put("orderCode", Long.parseLong(payment.getOrderCode()));
        body.put("amount", Long.parseLong(amount));

        // Các trường mô tả/callback/hạn/chữ ký.
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("expiredAt", expiredAt);
        body.put("signature", signature);

        try {
            // Tạo HTTP POST tới endpoint payment-requests.
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    // Body là JSON.
                    .header("Content-Type", "application/json")
                    // Hai header xác thực merchant do payOS cấp.
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    // Timeout riêng cho request.
                    .timeout(REQUEST_TIMEOUT)
                    // Serialize body Map thành JSON string.
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            // Gửi đồng bộ và đọc response body dạng String.
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Parse JSON response thành Map.
            Map<String, Object> result = gson.fromJson(response.body(), MAP_TYPE);

            // checkoutUrl nằm trong object data.
            Object data = result.get("data");
            if (data instanceof Map<?, ?> dataMap && dataMap.get("checkoutUrl") != null) {
                // URL này được controller dùng trong redirect.
                return dataMap.get("checkoutUrl").toString();
            }

            // Response thiếu checkoutUrl được coi là lỗi tích hợp.
            throw new IllegalArgumentException("payOS không trả về checkoutUrl: " + response.body());
        } catch (HttpTimeoutException ex) {
            // Thông báo riêng để phân biệt timeout với lỗi payload.
            throw new IllegalArgumentException("payOS phản hồi quá thời gian cho phép.", ex);
        } catch (InterruptedException ex) {
            // Khôi phục interrupt flag theo chuẩn Java concurrency.
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Yêu cầu tạo link thanh toán payOS bị gián đoạn.", ex);
        } catch (Exception ex) {
            // Bao URI/network/JSON và chuyển thành lỗi nghiệp vụ cho controller.
            throw new IllegalArgumentException("Không thể tạo link thanh toán payOS: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GatewayCallback parseCallback(Map<String, String> params) {
        // Các field định danh/trạng thái lấy từ dữ liệu callback đã flatten.
        String orderCode = params.get("orderCode");
        String status = params.getOrDefault("status", "");
        String code = params.getOrDefault("code", "");

        // payOS có thể biểu diễn hủy bằng boolean cancel.
        boolean cancelled = Boolean.parseBoolean(params.getOrDefault("cancel", "false"));

        // Chữ ký chỉ được kiểm tra khi payload có signature và amount bắt buộc.
        boolean hasRequiredSignatureData = params.containsKey("signature")
                && !params.getOrDefault("signature", "").isBlank()
                && params.containsKey("amount")
                && !params.getOrDefault("amount", "").isBlank();

        // verifySignature dựng lại canonical data và so HMAC.
        boolean valid = hasRequiredSignatureData && verifySignature(params);

        // SUCCESS cần chữ ký đúng, không hủy và status/code biểu thị PAID.
        boolean success = valid && !cancelled && ("PAID".equalsIgnoreCase(status) || "00".equals(code));

        // Chuẩn hóa mã kết quả nội bộ về 00/CANCELLED/PENDING hoặc code gateway.
        String responseCode = success ? "00" : (cancelled ? "CANCELLED" : params.getOrDefault("code", status.isBlank() ? "PENDING" : status));

        // Ưu tiên reference, rồi paymentLinkId, cuối cùng orderCode.
        String transactionId = params.getOrDefault("reference", params.getOrDefault("paymentLinkId", orderCode));

        // Lấy mô tả lỗi/trạng thái để lưu Payment.errorMessage.
        String message = params.getOrDefault("desc", params.getOrDefault("status", ""));

        // Trả record chuẩn hóa chung cho PaymentController.
        return new GatewayCallback(valid, orderCode, success, responseCode, transactionId, message);
    }

    @Override
    public GatewayPaymentStatus queryPayment(String orderCode) {
        // Đối soát không thể chạy khi gateway thiếu credential/URL.
        if (!isConfigured()) {
            throw new IllegalArgumentException("payOS chưa được cấu hình để đối soát giao dịch.");
        }
        try {
            // Ghép endpoint collection với orderCode đã URL encode.
            String queryUrl = endpoint.replaceAll("/+$", "") + "/" + PaymentGatewayUtils.urlEncode(orderCode);

            // Tạo request GET có hai header xác thực merchant.
            HttpRequest request = HttpRequest.newBuilder(URI.create(queryUrl))
                    .header("Accept", "application/json")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            // Gửi request và nhận cả HTTP status lẫn body.
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Parser kiểm tra status, schema, orderCode và chuẩn hóa kết quả.
            return parsePaymentStatusResponse(orderCode, response.statusCode(), response.body());
        } catch (HttpTimeoutException ex) {
            throw new IllegalArgumentException("Không thể đối soát payOS vì yêu cầu quá thời gian.", ex);
        } catch (InterruptedException ex) {
            // Khôi phục interrupt flag trước khi đổi exception.
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Yêu cầu đối soát payOS bị gián đoạn.", ex);
        } catch (IllegalArgumentException ex) {
            // Giữ nguyên message validation đã được parser tạo.
            throw ex;
        } catch (Exception ex) {
            // Bao các lỗi network/URI/JSON còn lại.
            throw new IllegalArgumentException("Không thể đối soát trạng thái với payOS: " + ex.getMessage(), ex);
        }
    }

    GatewayPaymentStatus parsePaymentStatusResponse(String requestedOrderCode, int httpStatus, String responseBody) {
        // Chỉ HTTP 2xx được parse như phản hồi thành công.
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("payOS trả về lỗi HTTP " + httpStatus + " khi đối soát giao dịch.");
        }

        // Deserialize body JSON.
        Map<String, Object> result = gson.fromJson(responseBody, MAP_TYPE);

        // payOS code "00" biểu thị request API thành công.
        if (result == null || !"00".equals(stringValue(result.get("code")))) {
            throw new IllegalArgumentException("payOS không trả về trạng thái giao dịch hợp lệ.");
        }

        // Dữ liệu giao dịch nằm trong object data.
        Object rawData = result.get("data");
        if (!(rawData instanceof Map<?, ?> data)) {
            throw new IllegalArgumentException("Phản hồi đối soát payOS bị thiếu dữ liệu.");
        }

        // Chuẩn hóa orderCode response về chuỗi không có .0.
        String returnedOrderCode = stringValue(data.get("orderCode"));

        // Chặn response của giao dịch khác.
        if (!requestedOrderCode.equals(returnedOrderCode)) {
            throw new IllegalArgumentException("Mã giao dịch payOS đối soát không khớp.");
        }

        // Parse amount để PaymentService so với amount nội bộ.
        BigDecimal amount = decimalValue(data.get("amount"));

        // Chuẩn hóa status uppercase cho switch.
        String status = stringValue(data.get("status")).toUpperCase();

        // Chỉ PAID là success.
        boolean success = "PAID".equals(status);

        // Ánh xạ status payOS thành responseCode nội bộ.
        String responseCode = success ? "00" : switch (status) {
            case "CANCELLED" -> "CANCELLED";
            case "PENDING", "PROCESSING" -> "PENDING";
            default -> status.isBlank() ? "PENDING" : status;
        };

        // Ưu tiên reference từ transaction gần nhất.
        String transactionId = transactionReference(data.get("transactions"));

        // Không có transaction reference thì dùng payment request ID.
        if (transactionId.isBlank()) {
            transactionId = stringValue(data.get("id"));
        }

        // Tạo message phù hợp; pending không ghi lỗi.
        String message = switch (status) {
            case "CANCELLED" -> "Khách hàng hủy thanh toán trên payOS.";
            case "PENDING", "PROCESSING" -> "";
            default -> stringValue(result.get("desc"));
        };

        // Record chuẩn hóa được PaymentService xác minh amount rồi xử lý.
        return new GatewayPaymentStatus(returnedOrderCode, amount, success, responseCode, transactionId, message);
    }

    private String transactionReference(Object transactions) {
        if (transactions instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> transaction) {
                    String reference = stringValue(transaction.get("reference"));
                    if (!reference.isBlank()) return reference;
                }
            }
        } else if (transactions instanceof Map<?, ?> transactionMap) {
            String directReference = stringValue(transactionMap.get("reference"));
            if (!directReference.isBlank()) return directReference;
            for (Object item : transactionMap.values()) {
                if (item instanceof Map<?, ?> transaction) {
                    String reference = stringValue(transaction.get("reference"));
                    if (!reference.isBlank()) return reference;
                }
            }
        }
        return "";
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null || stringValue(value).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Số tiền đối soát payOS không hợp lệ.", ex);
        }
    }

    private String stringValue(Object value) {
        if (value == null) return "";
        if (value instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private boolean verifySignature(Map<String, String> params) {
        // Tách signature nhận được khỏi dữ liệu dùng để tính.
        String receivedSignature = params.get("signature");

        // TreeMap sắp key tăng dần đúng canonical order của payOS.
        Map<String, String> data = new TreeMap<>(params);
        data.remove("signature");

        // Ghép key=value bằng dấu &, bỏ field có value null.
        String rawSignature = data.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        // Tính HMAC từ checksum key bí mật.
        String expectedSignature = PaymentGatewayUtils.hmacSha256(rawSignature, checksumKey);

        // So không phân biệt hoa/thường vì chữ ký hex có thể đổi casing.
        return receivedSignature != null && receivedSignature.equalsIgnoreCase(expectedSignature);
    }
}
