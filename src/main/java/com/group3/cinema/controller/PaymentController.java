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
    // Quản lý vòng đời Payment/Booking và xử lý kết quả cổng thanh toán.
    private final PaymentService paymentService;

    // Đọc snapshot chi tiết booking để render màn thanh toán/kết quả.
    private final CustomerBookingService bookingService;

    // Chọn đúng implementation gateway theo Payment.Method.
    private final PaymentGatewayRouter gatewayRouter;

    // Gửi thông báo nội bộ sau khi xác nhận thanh toán thành công.
    private final NotificationService notificationService;

    public PaymentController(PaymentService paymentService,
                             CustomerBookingService bookingService,
                             PaymentGatewayRouter gatewayRouter,
                             NotificationService notificationService) {
        // Constructor injection đảm bảo controller luôn có đủ bốn dependency bắt buộc.
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
            // Chỉ lấy Account từ server-side session; không nhận accountId từ query string.
            Account account = account(session);

            // Xác minh booking thuộc account, đang PENDING và chưa hết hạn.
            paymentService.requirePayableBooking(bookingId, account.getAccountID());

            // Header dùng user để hiển thị trạng thái đăng nhập.
            model.addAttribute("user", account);

            // Query chi tiết bằng cả bookingId và accountId để không lộ đơn của người khác.
            model.addAttribute("details", bookingService.getBookingDetails(bookingId, account.getAccountID()));

            // Render templates/payment.html.
            return "payment";
        } catch (IllegalArgumentException ex) {
            // Owner sai, đơn hết hạn hoặc chưa đăng nhập đều quay về catalog với flash error.
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
            // Xác định chủ booking từ session.
            Account account = account(session);

            // Tạo hoặc tái sử dụng Payment PENDING; số tiền được lấy từ Booking trong DB.
            Payment payment = paymentService.createPayment(bookingId, account.getAccountID(), method);

            // Gateway cần Booking để lấy tổng tiền, hạn thanh toán và mã mô tả.
            var details = bookingService.getBookingDetails(bookingId, account.getAccountID());

            // Router chọn PayOsGatewayService, gọi API và trả checkoutUrl.
            return "redirect:" + gatewayRouter.createRedirectUrl(payment, details.booking(), request);
        } catch (IllegalArgumentException ex) {
            // Giữ người dùng tại đúng booking để có thể thử lại sau lỗi network/validation.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/payment?bookingId=" + bookingId;
        }
    }

    @GetMapping("/payos/return")
    public String payosReturn(@RequestParam Map<String, String> params,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        // false cho biết đây là returnUrl thông thường, không phải cancelUrl.
        return handlePayOsBrowserReturn(params, session, redirectAttributes, false);
    }

    @GetMapping("/payos/cancel")
    public String payosCancel(@RequestParam Map<String, String> params,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        // true chỉ ảnh hưởng trạng thái hiển thị nếu giao dịch thực tế vẫn PENDING.
        return handlePayOsBrowserReturn(params, session, redirectAttributes, true);
    }

    @PostMapping("/payos/webhook")
    @ResponseBody
    /** Tách khối {@code data} và chữ ký webhook payOS về cấu trúc callback thống nhất. */
    public ResponseEntity<Map<String, Object>> payosWebhook(@RequestBody Map<String, Object> payload) {
        try {
            // Chuyển payload lồng nhau thành map chuỗi, xác minh chữ ký và cập nhật Payment/Booking.
            handleGatewayCallback(Payment.Method.PAYOS, stringifyPayOsWebhook(payload), null);

            // HTTP 200 thông báo cho payOS webhook đã được tiếp nhận.
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            // Chữ ký/payload/orderCode sai trả HTTP 400 và không cập nhật giao dịch.
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
            // Chỉ chấp nhận chuỗi số tối đa 50 chữ số trước khi dùng làm khóa tra cứu.
            if (!isValidOrderCode(orderCode)) {
                throw new IllegalArgumentException("Mã giao dịch thanh toán không hợp lệ.");
            }

            // Biến chứa trạng thái Payment nội bộ sau khi cố gắng đối soát.
            Payment payment;
            try {
                // Gọi trực tiếp API payOS; không tin trạng thái query string do browser gửi về.
                payment = paymentService.reconcilePayOsPayment(orderCode);
            } catch (IllegalArgumentException ex) {
                // Nếu payOS tạm lỗi, đọc bản ghi nội bộ để trang vẫn có dữ liệu hiển thị.
                payment = paymentService.getPaymentPublic(orderCode);

                // Cảnh báo trạng thái đang thấy có thể chưa phải trạng thái mới nhất.
                model.addAttribute("error", "Chưa thể đồng bộ trạng thái mới nhất từ payOS. " + ex.getMessage());
            }

            // Return URL là public nên user có thể null nếu session đã hết.
            model.addAttribute("user", session.getAttribute("loggedInUser"));

            // Truyền Payment và snapshot booking cho template kết quả.
            model.addAttribute("payment", payment);
            model.addAttribute("details", bookingService.getBookingDetails(payment.getBookingId()));

            // cancelUrl chỉ hiển thị CANCELLED khi DB vẫn PENDING; SUCCESS luôn được giữ nguyên.
            model.addAttribute("displayPaymentStatus",
                    cancelled && payment.getStatus() == Payment.Status.PENDING
                            ? Payment.Status.CANCELLED.name()
                            : payment.getStatus().name());

            // Render templates/payment-result.html.
            return "payment-result";
        } catch (IllegalArgumentException ex) {
            // Mã sai/không tồn tại đi sang trang lỗi thân thiện, không lộ stack trace.
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
        // Chỉ lấy orderCode để định danh; status/success từ browser return không được tin cậy.
        String orderCode = params.getOrDefault("orderCode", "").trim();

        // Chặn mã thiếu hoặc chứa ký tự lạ trước khi gọi service/API.
        if (!isValidOrderCode(orderCode)) {
            redirectAttributes.addFlashAttribute("error", "Mã giao dịch payOS không hợp lệ hoặc bị thiếu.");
            return "redirect:/payment/result";
        }
        try {
            // Đối soát server-to-server bằng thông tin xác thực của hệ thống.
            Payment payment = paymentService.reconcilePayOsPayment(orderCode);

            // Chỉ nguồn đối soát trả SUCCESS mới được xem là đã thanh toán.
            if (payment.getStatus() == Payment.Status.SUCCESS) {
                // Gửi thông báo phụ trợ sau khi trạng thái chính đã chốt.
                sendPaymentSuccessNotification(session, payment.getOrderCode());
                return "redirect:/my-tickets";
            }
        } catch (IllegalArgumentException ex) {
            // Vẫn mở trang kết quả và hiển thị lỗi đối soát để người dùng có thể tải lại.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        // cancelled=true chỉ điều khiển thông điệp UI, không tự sửa bản ghi Payment.
        return "redirect:/payment/result?orderCode=" + orderCode + (cancelled ? "&cancelled=true" : "");
    }

    private boolean isValidOrderCode(String orderCode) {
        // Regex yêu cầu toàn chữ số, độ dài 1..50; toán tử && loại null trước khi gọi matches.
        return orderCode != null && orderCode.matches("\\d{1,50}");
    }

    private Payment handleGatewayCallback(Payment.Method method, Map<String, String> params,
                                          RedirectAttributes redirectAttributes) {
        // Lấy implementation gateway và để gateway tự xác thực chữ ký/chuẩn hóa payload.
        PaymentGatewayService.GatewayCallback callback = gatewayRouter.gateway(method).parseCallback(params);

        // Không cập nhật Payment nếu chữ ký không hợp lệ.
        if (!callback.validSignature()) {
            // Browser callback có RedirectAttributes; webhook truyền null và tự tạo HTTP response.
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("error", "Chữ ký thanh toán không hợp lệ.");
            }
            throw new InvalidPaymentSignatureException("Invalid signature");
        }

        // Service xử lý idempotent và cập nhật đồng bộ Payment, Booking, ghế, vé, voucher.
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
        // Map đích dùng String cho cả key/value để phù hợp interface parseCallback chung.
        Map<String, String> result = new HashMap<>();

        // payOS đặt các trường được ký trong object data.
        Object data = payload.get("data");

        // Chỉ duyệt khi data thực sự là JSON object đã deserialize thành Map.
        if (data instanceof Map<?, ?> dataMap) {
            // Chuyển null thành chuỗi rỗng và kiểu số/boolean thành biểu diễn chuỗi.
            dataMap.forEach((key, value) -> result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        }

        // Chỉ thêm signature cấp ngoài; code/desc/success không thuộc dữ liệu được ký.
        result.put("signature", payload.get("signature") == null ? "" : String.valueOf(payload.get("signature")));
        return result;
    }

    private Account account(HttpSession session) {
        // Đọc danh tính đã được xác thực từ server-side session.
        Account account = (Account) session.getAttribute("loggedInUser");

        // Dừng nghiệp vụ thanh toán nếu session chưa đăng nhập hoặc đã hết hạn.
        if (account == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập.");
        }

        // Trả Account cho các method kiểm tra owner booking.
        return account;
    }

    /**
     * Gửi thông báo sau thanh toán thành công và dọn wishlist nếu booking bắt đầu từ đó.
     * Mọi lỗi phụ trợ đều bị cô lập để không biến một giao dịch đã trả tiền thành lỗi giao diện.
     */
    private void sendPaymentSuccessNotification(HttpSession session, String orderCode) {
        try {
            // Browser return có thể không còn phiên đăng nhập nên Account có thể null.
            Account account = (Account) session.getAttribute("loggedInUser");

            // Chỉ tạo notification/wishlist action khi xác định được đúng tài khoản.
            if (account != null) {
                // Ghi một Notification loại PAYMENT cho tài khoản vừa thanh toán.
                notificationService.sendNotification(
                        account.getAccountID(),
                        "Thanh toán thành công \uD83D\uDCB8",
                        "Giao dịch cho mã thanh toán " + orderCode + " đã hoàn tất. Bạn có thể kiểm tra vé trong mục 'Vé của tôi'!",
                        NotificationType.PAYMENT
                );

                // Đọc Payment để lần ngược tới booking và phim tương ứng.
                Payment payment = paymentService.getPaymentPublic(orderCode);
                if (payment != null) {
                    // Service chỉ xóa nếu session có cờ from_wishlist_movie_{movieId}.
                    paymentService.cleanWishlistIfFromWishlist(session, payment);
                }
            }
        } catch (Exception e) {
            // Notification/wishlist là hậu xử lý; lỗi ở đây không được phủ nhận giao dịch đã SUCCESS.
        }
    }
}
