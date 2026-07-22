/**
 * Service định tuyến và điều phối các cổng thanh toán (Payment Gateway Router).
 * 
 * Luồng gọi & Sử dụng:
 * - Được Spring tự động inject tất cả các Bean triển khai `PaymentGatewayService` (`PayOsGatewayService`, `VnPayGatewayService`, `MomoGatewayService`).
 * - Được gọi bởi `CustomerBookingService` để lấy service của cổng thanh toán tương ứng (`gateway(method)`) hoặc tạo URL chuyển hướng giao diện mô phỏng (`createRedirectUrl`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (25/06/2026)
 */
package com.group3.cinema.service.payment;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentGatewayRouter {
    private final Map<Payment.Method, PaymentGatewayService> gateways = new EnumMap<>(Payment.Method.class);

    /**
     * Constructor đăng ký tự động danh sách các Service cổng thanh toán khả dụng vào EnumMap.
     * 
     * @param gatewayServices Danh sách tất cả các triển khai PaymentGatewayService.
     */
    public PaymentGatewayRouter(List<PaymentGatewayService> gatewayServices) {
        gatewayServices.forEach(gateway -> gateways.put(gateway.method(), gateway));
    }

    /**
     * Tạo URL chuyển hướng sang giao diện mô phỏng thanh toán nội bộ hệ thống Rạp phim cho mục đích demo/test.
     * 
     * @param payment Giao dịch thanh toán.
     * @param booking Đơn đặt vé.
     * @param request HttpServletRequest lấy context path.
     * @return URL dạng `/payment/gateway/{orderCode}`.
     */
    public String createRedirectUrl(Payment payment, Booking booking, HttpServletRequest request) {
        return request.getContextPath() + "/payment/gateway/" + payment.getOrderCode();
    }

    /**
     * Tra cứu Service xử lý cổng thanh toán tương ứng với phương thức thanh toán (`Payment.Method`).
     * 
     * @param method Phương thức thanh toán (PAYOS, VNPAY, MOMO).
     * @return Service triển khai PaymentGatewayService tương ứng.
     * @throws IllegalArgumentException nếu cổng thanh toán chưa được hỗ trợ.
     */
    public PaymentGatewayService gateway(Payment.Method method) {
        PaymentGatewayService gateway = gateways.get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("Cổng thanh toán chưa được hỗ trợ.");
        }
        return gateway;
    }
}

