package com.group3.cinema.service.payment;

/*
 * Added on 2026-06-25: VNPAY sandbox gateway integration kept for compatibility.
 * Updated on 2026-06-26: Disabled by configuration in the customer payment flow.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
public class VnPayGatewayService implements PaymentGatewayService {
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Value("${payment.vnpay.enabled:false}")
    private boolean enabled;

    @Value("${payment.vnpay.pay-url:}")
    private String payUrl;

    @Value("${payment.vnpay.tmn-code:}")
    private String tmnCode;

    @Value("${payment.vnpay.hash-secret:}")
    private String hashSecret;

    @Value("${payment.vnpay.return-url:}")
    private String returnUrl;

    @Override
    public Payment.Method method() {
        return Payment.Method.VNPAY;
    }

    @Override
    public boolean isConfigured() {
        return enabled && !payUrl.isBlank() && !tmnCode.isBlank() && !hashSecret.isBlank() && !returnUrl.isBlank();
    }

    @Override
    public String createPaymentUrl(Payment payment, Booking booking, HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", payment.getAmount().setScale(0, RoundingMode.DOWN).multiply(java.math.BigDecimal.valueOf(100)).toPlainString());
        params.put("vnp_CreateDate", payment.getCreatedAt().atZone(VIETNAM_ZONE).format(VNPAY_TIME));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", PaymentGatewayUtils.clientIp(request));
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderInfo", "Thanh toan ve CineFlow " + booking.getId());
        params.put("vnp_OrderType", "billpayment");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_TxnRef", payment.getOrderCode());
        params.put("vnp_ExpireDate", booking.getExpiresAt().atZone(VIETNAM_ZONE).format(VNPAY_TIME));

        String hashData = PaymentGatewayUtils.queryString(params, true);
        String secureHash = PaymentGatewayUtils.hmacSha512(hashData, hashSecret);
        return payUrl + "?" + hashData + "&vnp_SecureHash=" + secureHash;
    }

    @Override
    public GatewayCallback parseCallback(Map<String, String> params) {
        Map<String, String> signedParams = new TreeMap<>(params);
        String receivedHash = signedParams.remove("vnp_SecureHash");
        signedParams.remove("vnp_SecureHashType");
        String hashData = PaymentGatewayUtils.queryString(signedParams, true);
        String expectedHash = PaymentGatewayUtils.hmacSha512(hashData, hashSecret);
        boolean valid = receivedHash != null && receivedHash.equalsIgnoreCase(expectedHash);
        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String transactionStatus = params.getOrDefault("vnp_TransactionStatus", "");
        boolean success = valid && "00".equals(responseCode) && "00".equals(transactionStatus);
        return new GatewayCallback(valid, params.get("vnp_TxnRef"), success, responseCode,
                params.get("vnp_TransactionNo"), params.getOrDefault("vnp_OrderInfo", ""));
    }
}
