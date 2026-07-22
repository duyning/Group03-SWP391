package com.group3.cinema.service.payment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayOsGatewayServiceTest {

    @Test
    void callbackWithoutSignatureIsRejected() {
        PayOsGatewayService gateway = gateway("test-checksum-key");

        PaymentGatewayService.GatewayCallback callback = gateway.parseCallback(Map.of(
                "orderCode", "123456",
                "amount", "100000",
                "status", "PAID"
        ));

        assertFalse(callback.validSignature());
        assertFalse(callback.success());
    }

    @Test
    void callbackWithValidSignatureIsAccepted() {
        String checksumKey = "test-checksum-key";
        PayOsGatewayService gateway = gateway(checksumKey);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", "100000");
        params.put("orderCode", "123456");
        params.put("status", "PAID");
        params.put("signature", PaymentGatewayUtils.hmacSha256(
                "amount=100000&orderCode=123456",
                checksumKey
        ));

        PaymentGatewayService.GatewayCallback callback = gateway.parseCallback(params);

        assertTrue(callback.validSignature());
        assertTrue(callback.success());
    }

    private PayOsGatewayService gateway(String checksumKey) {
        PayOsGatewayService gateway = new PayOsGatewayService();
        ReflectionTestUtils.setField(gateway, "checksumKey", checksumKey);
        return gateway;
    }
}
