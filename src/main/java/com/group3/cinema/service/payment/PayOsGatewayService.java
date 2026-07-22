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
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Gson gson = new Gson();
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
        return Payment.Method.PAYOS;
    }

    @Override
    public boolean isConfigured() {
        return enabled && !endpoint.isBlank() && !clientId.isBlank() && !apiKey.isBlank()
                && !checksumKey.isBlank() && !returnUrl.isBlank() && !cancelUrl.isBlank();
    }

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
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = gson.fromJson(response.body(), MAP_TYPE);
            Object data = result.get("data");
            if (data instanceof Map<?, ?> dataMap && dataMap.get("checkoutUrl") != null) {
                return dataMap.get("checkoutUrl").toString();
            }
            throw new IllegalArgumentException("payOS không trả về checkoutUrl: " + response.body());
        } catch (HttpTimeoutException ex) {
            throw new IllegalArgumentException("payOS phản hồi quá thời gian cho phép.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Yêu cầu tạo link thanh toán payOS bị gián đoạn.", ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không thể tạo link thanh toán payOS: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GatewayCallback parseCallback(Map<String, String> params) {
        String orderCode = params.get("orderCode");
        String status = params.getOrDefault("status", "");
        String code = params.getOrDefault("code", "");
        boolean cancelled = Boolean.parseBoolean(params.getOrDefault("cancel", "false"));
        boolean hasRequiredSignatureData = params.containsKey("signature")
                && !params.getOrDefault("signature", "").isBlank()
                && params.containsKey("amount")
                && !params.getOrDefault("amount", "").isBlank();
        boolean valid = hasRequiredSignatureData && verifySignature(params);
        boolean success = valid && !cancelled && ("PAID".equalsIgnoreCase(status) || "00".equals(code));
        String responseCode = success ? "00" : (cancelled ? "CANCELLED" : params.getOrDefault("code", status.isBlank() ? "PENDING" : status));
        String transactionId = params.getOrDefault("reference", params.getOrDefault("paymentLinkId", orderCode));
        String message = params.getOrDefault("desc", params.getOrDefault("status", ""));
        return new GatewayCallback(valid, orderCode, success, responseCode, transactionId, message);
    }

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
