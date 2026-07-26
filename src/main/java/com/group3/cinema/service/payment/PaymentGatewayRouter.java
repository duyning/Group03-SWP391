package com.group3.cinema.service.payment;

/*
 * Added on 2026-06-25: Selects the configured payment gateway for each payment method.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentGatewayRouter {
    // EnumMap lưu một implementation gateway cho mỗi Payment.Method.
    private final Map<Payment.Method, PaymentGatewayService> gateways = new EnumMap<>(Payment.Method.class);

    public PaymentGatewayRouter(List<PaymentGatewayService> gatewayServices) {
        // Spring inject mọi implementation; mỗi bean tự khai báo enum qua method().
        gatewayServices.forEach(gateway -> gateways.put(gateway.method(), gateway));
    }

    public String createRedirectUrl(Payment payment, Booking booking, HttpServletRequest request) {
        // Chọn implementation theo paymentMethod đã được lưu trong Payment.
        PaymentGatewayService gateway = gateway(payment.getPaymentMethod());

        // Chặn trước khi gọi API nếu thiếu enabled/key/URL.
        if (!gateway.isConfigured()) {
            throw new IllegalArgumentException(
                    "payOS chưa được cấu hình. Vui lòng kiểm tra PAYOS_ENABLED, PAYOS_CLIENT_ID, "
                    + "PAYOS_API_KEY, PAYOS_CHECKSUM_KEY, PAYOS_RETURN_URL và PAYOS_CANCEL_URL."
            );
        }

        // Delegate việc tạo checkout URL cho gateway cụ thể.
        return gateway.createPaymentUrl(payment, booking, request);
    }

    public PaymentGatewayService gateway(Payment.Method method) {
        // Lookup implementation O(1) từ EnumMap.
        PaymentGatewayService gateway = gateways.get(method);

        // Không có bean đăng ký cho method thì dừng bằng lỗi nghiệp vụ.
        if (gateway == null) {
            throw new IllegalArgumentException("Cổng thanh toán chưa được hỗ trợ.");
        }

        // Trả gateway để create/parse/query.
        return gateway;
    }
}
