/**
 * Lớp tiện ích hỗ trợ tính toán mã băm SHA-256 / SHA-512, mã hóa URL và trích xuất IP khách hàng cho các Cổng thanh toán.
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi nội bộ bởi `PayOsGatewayService`, `VnPayGatewayService`, `MomoGatewayService` để tạo Chữ ký bảo mật HMAC và Query string.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (25/06/2026)
 */
package com.group3.cinema.service.payment;

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

    /**
     * Tạo mã băm HMAC-SHA512 từ chuỗi dữ liệu đầu vào và Khóa bí mật (Secret key).
     */
    static String hmacSha512(String data, String secret) {
        return hmac(data, secret, "HmacSHA512");
    }

    /**
     * Tạo mã băm HMAC-SHA256 từ chuỗi dữ liệu đầu vào và Khóa bí mật (Secret key).
     */
    static String hmacSha256(String data, String secret) {
        return hmac(data, secret, "HmacSHA256");
    }

    /**
     * Hàm dùng chung tính toán mã băm mã hóa HMAC theo thuật toán tùy chọn (HmacSHA256, HmacSHA512).
     */
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

    /**
     * Chuyển đổi Map các tham số thành chuỗi Query String đã sắp xếp theo thứ tự bảng chữ cái alphabet của key.
     * 
     * @param params Map chứa tham số.
     * @param encode Có mã hóa URLEncoder cho key/value hay không.
     * @return Chuỗi query dạng `key1=val1&key2=val2`.
     */
    static String queryString(Map<String, String> params, boolean encode) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode
                        ? urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue())
                        : entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * Mã hóa chuỗi ký tự sang định dạng UTF-8 URL Encoding.
     */
    static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Trích xuất địa chỉ IP của Client từ HttpServletRequest (xử lý proxy X-Forwarded-For).
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "127.0.0.1" : request.getRemoteAddr();
    }
}

