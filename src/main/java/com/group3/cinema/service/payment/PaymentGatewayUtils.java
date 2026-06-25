package com.group3.cinema.service.payment;

/*
 * Added on 2026-06-25: Shared checksum and URL utilities for payment gateways.
 * Created by: HuyPB - HE191335
 */

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class PaymentGatewayUtils {
    private PaymentGatewayUtils() {
    }

    static String hmacSha512(String data, String secret) {
        return hmac(data, secret, "HmacSHA512");
    }

    static String hmacSha256(String data, String secret) {
        return hmac(data, secret, "HmacSHA256");
    }

    static String hmac(String data, String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo chữ ký thanh toán.", ex);
        }
    }

    static String queryString(Map<String, String> params, boolean encode) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode
                        ? urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue())
                        : entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "127.0.0.1" : request.getRemoteAddr();
    }
}
