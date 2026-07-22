package com.group3.cinema.controller;

/*
 * Added on 2026-06-24: Customer payment endpoints for booking flow.
 * Updated on 2026-06-26: Public gateway return/cancel pages are normalized for payOS flow.
 * Added Notification on Success Payment.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.service.CustomerBookingService;
import com.group3.cinema.service.NotificationService;
import com.group3.cinema.service.PaymentService;
import com.group3.cinema.service.payment.PaymentGatewayRouter;
import com.group3.cinema.service.payment.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/payment")
public class PaymentController {
    private final PaymentService paymentService;
    private final CustomerBookingService bookingService;
    private final PaymentGatewayRouter gatewayRouter;
    private final NotificationService notificationService; // Thêm Service thông báo

    public PaymentController(PaymentService paymentService,
                             CustomerBookingService bookingService,
                             PaymentGatewayRouter gatewayRouter,
                             NotificationService notificationService) {
        this.paymentService = paymentService;
        this.bookingService = bookingService;
        this.gatewayRouter = gatewayRouter;
        this.notificationService = notificationService;
    }

    @GetMapping
    /**
     * Mở màn chọn phương thức thanh toán cho một booking.
     * Chỉ chủ sở hữu đơn đang ở trạng thái PENDING và chưa hết hạn mới được xem.
     */
    public String payment(@RequestParam("bookingId") Long bookingId, HttpSession session,
                          Model model, RedirectAttributes redirectAttributes) {
        try {
            Account account = account(session);
            paymentService.requirePayableBooking(bookingId, account.getAccountID());
            model.addAttribute("user", account);
            model.addAttribute("details", bookingService.getBookingDetails(bookingId, account.getAccountID()));
            return "payment";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/movies";
        }
    }

    @PostMapping("/start")
    /**
     * Tạo giao dịch PENDING và chuyển người dùng sang cổng thanh toán phù hợp.
     * Nếu đơn đã có giao dịch đang chờ, PaymentService tái sử dụng giao dịch đó
     * để tránh sinh nhiều mã thanh toán cho cùng một booking.
     */
    public String start(@RequestParam Long bookingId, @RequestParam String method,
                        HttpSession session, HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {
        try {
            Account account = account(session);
            Payment payment = paymentService.createPayment(bookingId, account.getAccountID(), method);
            var details = bookingService.getBookingDetails(bookingId, account.getAccountID());
            return "redirect:" + gatewayRouter.createRedirectUrl(payment, details.booking(), request);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/payment?bookingId=" + bookingId;
        }
    }

    @GetMapping("/payos/return")
    public String payosReturn(@RequestParam Map<String, String> params,
                              HttpSession session, // Bổ sung Session
                              RedirectAttributes redirectAttributes) {
        return handlePayOsBrowserReturn(params, session, redirectAttributes, false);
    }

    @GetMapping("/payos/cancel")
    public String payosCancel(@RequestParam Map<String, String> params,
                              HttpSession session, // Bổ sung Session
                              RedirectAttributes redirectAttributes) {
        return handlePayOsBrowserReturn(params, session, redirectAttributes, true);
    }

    @PostMapping("/payos/webhook")
    @ResponseBody
    /** Tách khối {@code data} và chữ ký webhook payOS về cấu trúc callback thống nhất. */
    public ResponseEntity<Map<String, Object>> payosWebhook(@RequestBody Map<String, Object> payload) {
        try {
            handleGatewayCallback(Payment.Method.PAYOS, stringifyPayOsWebhook(payload), null);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @GetMapping("/result")
    /**
     * Hiển thị kết quả thanh toán theo orderCode.
     * Endpoint này đọc công khai để cổng thanh toán có thể chuyển về ngay cả khi session thay đổi,
     * nhưng chỉ hiển thị dữ liệu giao dịch/đơn cần thiết trên trang kết quả.
     */
    public String result(@RequestParam(required = false) String orderCode,
                         @RequestParam(defaultValue = "false") boolean cancelled,
                         HttpSession session, Model model) {
        try {
            if (!isValidOrderCode(orderCode)) {
                throw new IllegalArgumentException("Mã giao dịch thanh toán không hợp lệ.");
            }
            Payment payment;
            try {
                // Đối soát với payOS trước khi áp dụng hết hạn cục bộ để không bỏ sót giao dịch đã PAID.
                payment = paymentService.reconcilePayOsPayment(orderCode);
            } catch (IllegalArgumentException ex) {
                payment = paymentService.getPaymentPublic(orderCode);
                model.addAttribute("error", "Chưa thể đồng bộ trạng thái mới nhất từ payOS. " + ex.getMessage());
            }
            model.addAttribute("user", session.getAttribute("loggedInUser"));
            model.addAttribute("payment", payment);
            model.addAttribute("details", bookingService.getBookingDetails(payment.getBookingId()));
            model.addAttribute("displayPaymentStatus",
                    cancelled && payment.getStatus() == Payment.Status.PENDING
                            ? Payment.Status.CANCELLED.name()
                            : payment.getStatus().name());
            return "payment-result";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("user", session.getAttribute("loggedInUser"));
            model.addAttribute("errorMessage", ex.getMessage());
            return "payment-error";
        }
    }

    /**
     * returnUrl/cancelUrl của payOS chỉ dùng để đưa trình duyệt về website và không có chữ ký.
     * Vì vậy không tin trực tiếp query string; hệ thống gọi API payOS có client-id/api-key để đối soát.
     */
    private String handlePayOsBrowserReturn(Map<String, String> params, HttpSession session,
                                            RedirectAttributes redirectAttributes, boolean cancelled) {
        String orderCode = params.getOrDefault("orderCode", "").trim();
        if (!isValidOrderCode(orderCode)) {
            redirectAttributes.addFlashAttribute("error", "Mã giao dịch payOS không hợp lệ hoặc bị thiếu.");
            return "redirect:/payment/result";
        }
        try {
            Payment payment = paymentService.reconcilePayOsPayment(orderCode);
            if (payment.getStatus() == Payment.Status.SUCCESS) {
                sendPaymentSuccessNotification(session, payment.getOrderCode());
                return "redirect:/my-tickets";
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/payment/result?orderCode=" + orderCode + (cancelled ? "&cancelled=true" : "");
    }

    private boolean isValidOrderCode(String orderCode) {
        return orderCode != null && orderCode.matches("\\d{1,50}");
    }

    private Payment handleGatewayCallback(Payment.Method method, Map<String, String> params,
                                          RedirectAttributes redirectAttributes) {
        // Mỗi gateway tự xác thực chữ ký và chuẩn hóa kết quả về cùng một GatewayCallback.
        PaymentGatewayService.GatewayCallback callback = gatewayRouter.gateway(method).parseCallback(params);
        if (!callback.validSignature()) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("error", "Chữ ký thanh toán không hợp lệ.");
            }
            throw new InvalidPaymentSignatureException("Invalid signature");
        }
        return paymentService.processGatewayResult(callback.orderCode(), callback.success(),
                callback.responseCode(), callback.transactionId(), callback.message());
    }

    private static final class InvalidPaymentSignatureException extends IllegalArgumentException {
        private InvalidPaymentSignatureException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringifyPayOsWebhook(Map<String, Object> payload) {
        // payOS đặt dữ liệu giao dịch trong "data", còn chữ ký và trạng thái nằm ở cấp ngoài.
        Map<String, String> result = new HashMap<>();
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            dataMap.forEach((key, value) -> result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        }
        // Chữ ký payOS được tạo từ đúng object "data"; không trộn code/desc/success ở cấp ngoài vào.
        result.put("signature", payload.get("signature") == null ? "" : String.valueOf(payload.get("signature")));
        return result;
    }

    private Account account(HttpSession session) {
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập.");
        }
        return account;
    }

    /**
     * Gửi thông báo sau thanh toán thành công và dọn wishlist nếu booking bắt đầu từ đó.
     * Mọi lỗi phụ trợ đều bị cô lập để không biến một giao dịch đã trả tiền thành lỗi giao diện.
     */
    private void sendPaymentSuccessNotification(HttpSession session, String orderCode) {
        try {
            Account account = (Account) session.getAttribute("loggedInUser");
            if (account != null) {
                notificationService.sendNotification(
                        account.getAccountID(),
                        "Thanh toán thành công \uD83D\uDCB8",
                        "Giao dịch cho mã thanh toán " + orderCode + " đã hoàn tất. Bạn có thể kiểm tra vé trong mục 'Vé của tôi'!",
                        NotificationType.PAYMENT
                );
                
                // Chỉ xóa phim khỏi wishlist khi session ghi nhận người dùng đi vào booking từ wishlist.
                Payment payment = paymentService.getPaymentPublic(orderCode);
                if (payment != null) {
                    paymentService.cleanWishlistIfFromWishlist(session, payment);
                }
            }
        } catch (Exception e) {
            // Bắt lỗi để nếu có trục trặc phần thông báo cũng không làm hỏng luồng thanh toán chính
        }
    }
}
