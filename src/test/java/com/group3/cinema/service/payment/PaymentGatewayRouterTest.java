package com.group3.cinema.service.payment;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentGatewayRouterTest {

    @Test
    void configuredPayOsReturnsRealCheckoutUrl() {
        PaymentGatewayService payOs = mock(PaymentGatewayService.class);
        Payment payment = payment(Payment.Method.PAYOS);
        Booking booking = mock(Booking.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        String checkoutUrl = "https://pay.payos.vn/web/checkout-link";

        when(payOs.method()).thenReturn(Payment.Method.PAYOS);
        when(payOs.isConfigured()).thenReturn(true);
        when(payOs.createPaymentUrl(payment, booking, request)).thenReturn(checkoutUrl);

        PaymentGatewayRouter router = new PaymentGatewayRouter(List.of(payOs));

        assertEquals(checkoutUrl, router.createRedirectUrl(payment, booking, request));
        verify(payOs).createPaymentUrl(payment, booking, request);
    }

    @Test
    void unconfiguredPayOsFailsInsteadOfUsingInternalGateway() {
        PaymentGatewayService payOs = mock(PaymentGatewayService.class);
        Payment payment = payment(Payment.Method.PAYOS);
        Booking booking = mock(Booking.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(payOs.method()).thenReturn(Payment.Method.PAYOS);
        when(payOs.isConfigured()).thenReturn(false);

        PaymentGatewayRouter router = new PaymentGatewayRouter(List.of(payOs));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> router.createRedirectUrl(payment, booking, request)
        );

        assertTrue(exception.getMessage().contains("payOS chưa được cấu hình"));
        verify(payOs, never()).createPaymentUrl(payment, booking, request);
    }

    private Payment payment(Payment.Method method) {
        Payment payment = new Payment();
        payment.setPaymentMethod(method);
        return payment;
    }
}
