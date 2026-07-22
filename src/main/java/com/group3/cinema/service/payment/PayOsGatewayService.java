/**
 * Service tích hợp Cổng thanh toán quét mã QR trực tuyến PayOS / VietQR cho đơn đặt vé xem phim (`PaymentGatewayService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được điều phối bởi `PaymentGatewayRouter` và `CustomerBookingService`.
 * - Tự động gọi API payOS để tạo liên kết thanh toán (`createPaymentUrl`) kèm chữ ký SHA-256 bảo mật.
 * - Kiểm tra chữ ký Callback (`parseCallback`, `verifySignature`) gửi từ payOS để xác nhận trạng thái thanh toán thành công.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (26/06/2026)
 */
package com.group3.cinema.service.payment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PayOsGatewayService implements PaymentGatewayService {
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

    /**
     * Xác định phương thức thanh toán là PAYOS.
     */
    @Override
    public Payment.Method method() {
        return Payment.Method.PAYOS;
    }

    /**
     * Kiểm tra trạng thái kích hoạt và cấu hình đầy đủ của payOS trong `application.properties`.
     */
    @Override
    public boolean isConfigured() {
        return enabled && !endpoint.isBlank() && !clientId.isBlank() && !apiKey.isBlank()
                && !checksumKey.isBlank() && !returnUrl.isBlank() && !cancelUrl.isBlank();
    }

    /**
     * Tạo liên kết trang thanh toán VietQR payOS cho đơn đặt vé xem phim.
     * 
     * @param payment Giao dịch thanh toán.
     * @param booking Đơn đặt vé.
     * @param request HttpServletRequest.
     * @return Đường dẫn checkoutUrl từ payOS.
     */
    @Override
    public String createPaymentUrl(Payment payment, Booking booking, HttpServletRequest request) {
        String amount = payment.getAmount().setScale(0, RoundingMode.DOWN).toPlainString();
        String description = ("CF" + booking.getId() + " VE").replaceAll("[^A-Za-z0-9 ]", "");
        long expiredAt = booking.getExpiresAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        String rawSignature = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + payment.getOrderCode()
                + "&returnUrl=" + returnUrl;
        String signature = PaymentGatewayUtils.hmacSha256(rawSignature, checksumKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", Long.parseLong(payment.getOrderCode()));
        body.put("amount", Long.parseLong(amount));
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("expiredAt", expiredAt);
        body.put("signature", signature);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = gson.fromJson(response.body(), MAP_TYPE);
            Object data = result.get("data");
            if (data instanceof Map<?, ?> dataMap && dataMap.get("checkoutUrl") != null) {
                return dataMap.get("checkoutUrl").toString();
            }
            throw new IllegalArgumentException("payOS không trả về checkoutUrl: " + response.body());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không thể tạo link thanh toán payOS: " + ex.getMessage(), ex);
        }
    }

    /**
     * Giải mã tham số callback và kiểm tra tính hợp lệ của giao dịch payOS.
     */
    @Override
    public GatewayCallback parseCallback(Map<String, String> params) {
        String orderCode = params.get("orderCode");
        String status = params.getOrDefault("status", "");
        String code = params.getOrDefault("code", "");
        boolean cancelled = Boolean.parseBoolean(params.getOrDefault("cancel", "false"));
        boolean hasSignature = params.containsKey("signature") && params.containsKey("amount");
        boolean valid = !hasSignature || verifySignature(params);
        boolean success = valid && !cancelled && ("PAID".equalsIgnoreCase(status) || "00".equals(code));
        String responseCode = success ? "00" : (cancelled ? "CANCELLED" : params.getOrDefault("code", status.isBlank() ? "PENDING" : status));
        String transactionId = params.getOrDefault("reference", params.getOrDefault("paymentLinkId", orderCode));
        String message = params.getOrDefault("desc", params.getOrDefault("status", ""));
        return new GatewayCallback(valid, orderCode, success, responseCode, transactionId, message);
    }

    /**
     * Kiểm tra đối chiếu chữ ký bảo mật SHA-256 phản hồi từ payOS.
     */
    private boolean verifySignature(Map<String, String> params) {
        String receivedSignature = params.get("signature");
        Map<String, String> data = new TreeMap<>(params);
        data.remove("signature");
        data.remove("code");
        data.remove("desc");
        data.remove("success");
        data.remove("status");
        data.remove("cancel");
        String rawSignature = data.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        String expectedSignature = PaymentGatewayUtils.hmacSha256(rawSignature, checksumKey);
        return receivedSignature != null && receivedSignature.equalsIgnoreCase(expectedSignature);
    }
}

