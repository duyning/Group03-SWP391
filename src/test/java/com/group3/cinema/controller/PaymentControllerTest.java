package com.group3.cinema.controller;

import com.group3.cinema.entity.Payment;
import com.group3.cinema.service.CustomerBookingService;
import com.group3.cinema.service.NotificationService;
import com.group3.cinema.service.PaymentService;
import com.group3.cinema.service.payment.PayOsGatewayService;
import com.group3.cinema.service.payment.PaymentGatewayRouter;
import com.group3.cinema.service.payment.PaymentGatewayService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;
    @Mock
    private CustomerBookingService bookingService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private HttpSession session;
    @Mock
    private PaymentGatewayService payOsGateway;

    private PaymentGatewayRouter gatewayRouter;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        when(payOsGateway.method()).thenReturn(Payment.Method.PAYOS);
        gatewayRouter = new PaymentGatewayRouter(List.of(payOsGateway));
        controller = new PaymentController(paymentService, bookingService, gatewayRouter, notificationService);
    }

    @Test
    void browserReturnWithoutSignatureReconcilesThroughAuthenticatedPayOsApi() {
        Payment payment = payment("123456", Payment.Status.PENDING);
        when(paymentService.reconcilePayOsPayment("123456")).thenReturn(payment);

        String view = controller.payosReturn(Map.of(
                "code", "00",
                "cancel", "false",
                "status", "PAID",
                "orderCode", "123456"
        ), session, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/payment/result?orderCode=123456");
        verify(paymentService).reconcilePayOsPayment("123456");
    }

    @Test
    void missingOrderCodeRendersFriendlyPaymentErrorPage() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.result(null, false, session, model);

        assertThat(view).isEqualTo("payment-error");
        assertThat(model.get("errorMessage")).isEqualTo("Mã giao dịch thanh toán không hợp lệ.");
    }

    @Test
    void signedWebhookUsesAllFieldsInsideDataObject() {
        String checksumKey = "test-checksum-key";
        PayOsGatewayService actualPayOsGateway = new PayOsGatewayService();
        ReflectionTestUtils.setField(actualPayOsGateway, "checksumKey", checksumKey);
        PaymentController webhookController = new PaymentController(paymentService, bookingService,
                new PaymentGatewayRouter(List.of(actualPayOsGateway)), notificationService);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", 100000);
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("orderCode", 123456);
        data.put("reference", "TF230204212323");
        String signature = hmacSha256(
                "amount=100000&code=00&desc=Thành công&orderCode=123456&reference=TF230204212323",
                checksumKey
        );
        when(paymentService.processGatewayResult("123456", true, "00", "TF230204212323", "Thành công"))
                .thenReturn(payment("123456", Payment.Status.SUCCESS));

        ResponseEntity<Map<String, Object>> response = webhookController.payosWebhook(Map.of(
                "code", "00",
                "desc", "success",
                "success", true,
                "data", data,
                "signature", signature
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(paymentService).processGatewayResult("123456", true, "00", "TF230204212323", "Thành công");
    }

    private Payment payment(String orderCode, Payment.Status status) {
        Payment payment = new Payment();
        payment.setOrderCode(orderCode);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("75000"));
        return payment;
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder result = new StringBuilder();
            for (byte value : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
