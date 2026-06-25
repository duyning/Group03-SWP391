package com.group3.cinema.service.payment;

/*
 * Added on 2026-06-25: Common contract for external payment gateway integrations.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface PaymentGatewayService {
    Payment.Method method();

    boolean isConfigured();

    String createPaymentUrl(Payment payment, Booking booking, HttpServletRequest request);

    GatewayCallback parseCallback(Map<String, String> params);

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
