/**
 * Interface định nghĩa hợp đồng chung (Strategy Pattern) cho các Cổng thanh toán trực tuyến (PayOS, VNPay, MoMo).
 * 
 * Luồng gọi & Sử dụng:
 * - Được triển khai bởi `PayOsGatewayService`, `VnPayGatewayService`, `MomoGatewayService`.
 * - Được quản lý và điều phối qua `PaymentGatewayRouter` để xử lý tạo URL thanh toán và xác thực chữ ký Webhook/Callback.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (25/06/2026)
 */
package com.group3.cinema.service.payment;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface PaymentGatewayService {

    /**
     * Trả về phương thức thanh toán tương ứng mà triển khai này đại diện (PAYOS, VNPAY, MOMO).
     */
    Payment.Method method();

    /**
     * Kiểm tra xem các cấu hình API Key, Client ID, Secret Key của cổng thanh toán đã được khai báo đầy đủ hay chưa.
     */
    boolean isConfigured();

    /**
     * Khởi tạo đường dẫn redirect thanh toán (Payment URL) cho đơn đặt vé.
     * 
     * @param payment Giao dịch thanh toán cần thực hiện.
     * @param booking Đơn đặt vé chứa thông tin phim, ghế và số tiền.
     * @param request HttpServletRequest để lấy domain gốc callback.
     * @return URL để redirect khách sang cổng thanh toán.
     */
    String createPaymentUrl(Payment payment, Booking booking, HttpServletRequest request);

    /**
     * Phân tích và xác thực dữ liệu chữ ký (signature) từ kết quả phản hồi Callback/Webhook của cổng thanh toán.
     * 
     * @param params Map chứa toàn bộ tham số gửi về từ cổng thanh toán.
     * @return Đối tượng GatewayCallback chứa thông tin xác thực và mã giao dịch.
     */
    GatewayCallback parseCallback(Map<String, String> params);

    /**
     * Record chứa kết quả giải mã và kiểm tra chữ ký của callback từ cổng thanh toán.
     */
    record GatewayCallback(
            boolean validSignature,
            String orderCode,
            boolean success,
            String responseCode,
            String transactionId,
            String message
    ) {
    }
}

