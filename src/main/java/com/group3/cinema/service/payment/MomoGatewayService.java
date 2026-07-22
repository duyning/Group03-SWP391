/**
 * Service tích hợp Ví điện tử MoMo Sandbox cho đơn đặt vé xem phim (`PaymentGatewayService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được điều phối qua `PaymentGatewayRouter` để tạo URL ví MoMo (`createPaymentUrl`).
 * - Phân tích chữ ký HMAC-SHA256 (`parseCallback`) gửi từ MoMo IPN/Callback.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (25/06/2026)
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MomoGatewayService implements PaymentGatewayService {
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${payment.momo.enabled:false}")
    private boolean enabled;

    @Value("${payment.momo.endpoint:}")
    private String endpoint;

    @Value("${payment.momo.partner-code:}")
    private String partnerCode;

    @Value("${payment.momo.access-key:}")
    private String accessKey;

    @Value("${payment.momo.secret-key:}")
    private String secretKey;

    @Value("${payment.momo.return-url:}")
    private String returnUrl;

    @Value("${payment.momo.ipn-url:}")
    private String ipnUrl;

    /** Trả về phương thức thanh toán MOMO. */
    @Override
    public Payment.Method method() {
        return Payment.Method.MOMO;
    }

    /** Kiểm tra cấu hình MoMo đã sẵn sàng trong `application.properties` chưa. */
    @Override
    public boolean isConfigured() {
        return enabled && !endpoint.isBlank() && !partnerCode.isBlank() && !accessKey.isBlank()
                && !secretKey.isBlank() && !returnUrl.isBlank() && !ipnUrl.isBlank();
    }

    /** Tạo URL thanh toán qua ví MoMo bằng cách gửi Request HTTP POST kèm chữ ký HMAC-SHA256 sang cổng MoMo. */
    @Override
    public String createPaymentUrl(Payment payment, Booking booking, HttpServletRequest request) {
        String requestId = payment.getOrderCode() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String amount = payment.getAmount().setScale(0, RoundingMode.DOWN).toPlainString();
        String orderInfo = "Thanh toán vé CineFlow #" + booking.getId();
        String requestType = "captureWallet";
        String extraData = "";

        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + payment.getOrderCode()
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + returnUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;
        String signature = PaymentGatewayUtils.hmacSha256(rawSignature, secretKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("partnerName", "CineFlow");
        body.put("storeId", "CineFlow");
        body.put("requestId", requestId);
        body.put("amount", amount);
        body.put("orderId", payment.getOrderCode());
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", returnUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("lang", "vi");
        body.put("requestType", requestType);
        body.put("autoCapture", true);
        body.put("extraData", extraData);
        body.put("signature", signature);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = gson.fromJson(response.body(), MAP_TYPE);
            Object payUrl = result.get("payUrl");
            if (payUrl == null || payUrl.toString().isBlank()) {
                throw new IllegalArgumentException("MoMo không trả về payUrl: " + response.body());
            }
            return payUrl.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không thể tạo giao dịch MoMo: " + ex.getMessage(), ex);
        }
    }

    /** Giải mã và đối chiếu chữ ký dữ liệu phản hồi trả về từ ví MoMo. */
    @Override
    public GatewayCallback parseCallback(Map<String, String> params) {
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + params.getOrDefault("amount", "")
                + "&extraData=" + params.getOrDefault("extraData", "")
                + "&message=" + params.getOrDefault("message", "")
                + "&orderId=" + params.getOrDefault("orderId", "")
                + "&orderInfo=" + params.getOrDefault("orderInfo", "")
                + "&orderType=" + params.getOrDefault("orderType", "")
                + "&partnerCode=" + params.getOrDefault("partnerCode", "")
                + "&payType=" + params.getOrDefault("payType", "")
                + "&requestId=" + params.getOrDefault("requestId", "")
                + "&responseTime=" + params.getOrDefault("responseTime", "")
                + "&resultCode=" + params.getOrDefault("resultCode", "")
                + "&transId=" + params.getOrDefault("transId", "");
        String expectedSignature = PaymentGatewayUtils.hmacSha256(rawSignature, secretKey);
        String receivedSignature = params.get("signature");
        boolean valid = receivedSignature != null && receivedSignature.equalsIgnoreCase(expectedSignature);
        boolean success = valid && "0".equals(params.get("resultCode"));
        return new GatewayCallback(valid, params.get("orderId"), success,
                params.getOrDefault("resultCode", ""), params.get("transId"),
                params.getOrDefault("message", ""));
    }
}

