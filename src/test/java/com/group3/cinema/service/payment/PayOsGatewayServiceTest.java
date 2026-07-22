package com.group3.cinema.service.payment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;

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
        params.put("accountNumber", "12345678");
        params.put("amount", "100000");
        params.put("code", "00");
        params.put("desc", "Thành công");
        params.put("orderCode", "123456");
        params.put("reference", "TF230204212323");
        params.put("signature", PaymentGatewayUtils.hmacSha256(
                "accountNumber=12345678&amount=100000&code=00&desc=Thành công&orderCode=123456&reference=TF230204212323",
                checksumKey
        ));

        PaymentGatewayService.GatewayCallback callback = gateway.parseCallback(params);

        assertTrue(callback.validSignature());
        assertTrue(callback.success());
    }

    @Test
    void callbackSignatureMustCoverEveryWebhookDataField() {
        String checksumKey = "test-checksum-key";
        PayOsGatewayService gateway = gateway(checksumKey);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", "100000");
        params.put("code", "00");
        params.put("desc", "Thành công");
        params.put("orderCode", "123456");
        params.put("signature", PaymentGatewayUtils.hmacSha256(
                "amount=100000&orderCode=123456",
                checksumKey
        ));

        PaymentGatewayService.GatewayCallback callback = gateway.parseCallback(params);

        assertFalse(callback.validSignature());
        assertFalse(callback.success());
    }

    @Test
    void parsesAuthenticatedPaymentLookupResponse() {
        PayOsGatewayService gateway = gateway("test-checksum-key");
        String body = """
                {
                  "code":"00",
                  "desc":"success",
                  "data":{
                    "id":"link-123",
                    "orderCode":123456,
                    "amount":75000,
                    "status":"PAID",
                    "transactions":[{"reference":"FT242040001"}]
                  }
                }
                """;

        PaymentGatewayService.GatewayPaymentStatus result =
                gateway.parsePaymentStatusResponse("123456", 200, body);

        assertTrue(result.success());
        assertTrue(result.amount().compareTo(new BigDecimal("75000")) == 0);
        assertTrue("00".equals(result.responseCode()));
        assertTrue("FT242040001".equals(result.transactionId()));
    }

    private PayOsGatewayService gateway(String checksumKey) {
        PayOsGatewayService gateway = new PayOsGatewayService();
        ReflectionTestUtils.setField(gateway, "checksumKey", checksumKey);
        return gateway;
    }
}
