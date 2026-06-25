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
    private final Map<Payment.Method, PaymentGatewayService> gateways = new EnumMap<>(Payment.Method.class);

    public PaymentGatewayRouter(List<PaymentGatewayService> gatewayServices) {
        gatewayServices.forEach(gateway -> gateways.put(gateway.method(), gateway));
    }

    public String createRedirectUrl(Payment payment, Booking booking, HttpServletRequest request) {
        PaymentGatewayService gateway = gateways.get(payment.getPaymentMethod());
        if (gateway == null || !gateway.isConfigured()) {
            return request.getContextPath() + "/payment/gateway/" + payment.getOrderCode();
        }
        return gateway.createPaymentUrl(payment, booking, request);
    }

    public PaymentGatewayService gateway(Payment.Method method) {
        PaymentGatewayService gateway = gateways.get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("Cổng thanh toán chưa được hỗ trợ.");
        }
        return gateway;
    }
}
