package com.group3.cinema.controller.api;

import com.group3.cinema.service.CounterSaleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST API phục vụ chức năng bán vé tại quầy.
 *
 * Luồng gọi từ frontend:
 * - `counter-sales.html` gọi API chọn suất/ghế:
 *   + GET  `/showtimes` để lấy suất còn bán.
 *   + GET  `/seats` để lấy sơ đồ ghế theo suất.
 *   + POST `/hold` để giữ ghế tạm.
 *   + POST `/release` để bỏ giữ ghế khi đổi ghế/quay lại.
 * - `counter-sales-checkout.html` gọi API thanh toán:
 *   + GET  `/customers` để gợi ý khách thành viên.
 *   + GET  `/combos` để lấy combo đang bán.
 *   + GET  `/vouchers` để lấy voucher còn hiệu lực.
 *   + POST `/preview` để tính hóa đơn nháp.
 *   + POST `/payment-link` để tạo đơn PENDING và link payOS.
 *   + POST `/complete` để chốt đơn tiền mặt.
 *
 * Controller này chỉ chuyển đổi HTTP <-> service response. Nghiệp vụ thật nằm trong `CounterSaleService`.
 */
@RestController
@RequestMapping("/api/counter-sales")
public class CounterSaleApiController {

    private final CounterSaleService counterSaleService;

    public CounterSaleApiController(CounterSaleService counterSaleService) {
        this.counterSaleService = counterSaleService;
    }

    /**
     * GET /api/counter-sales/showtimes
     *
     * Dùng ở bước 1 khi nhân viên chọn ngày hoặc nhập gợi ý tên phim.
     * API chỉ trả các suất còn bán: suất tương lai hoặc suất hôm nay nhưng chưa tới giờ bắt đầu.
     */
    @GetMapping("/showtimes")
    public ResponseEntity<?> showtimes(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "movieId", required = false) Integer movieId) {
        return handle(() -> counterSaleService.getSellableShowtimes(date, movieId));
    }

    /**
     * GET /api/counter-sales/customers
     *
     * Dùng ở bước checkout để tìm khách thành viên theo tên/email/số điện thoại.
     * Nếu không chọn khách thành viên, service sẽ gắn đơn với tài khoản khách vãng lai tại quầy.
     */
    @GetMapping("/customers")
    public ResponseEntity<?> customers(@RequestParam(value = "keyword", required = false) String keyword) {
        return handle(() -> counterSaleService.searchCustomers(keyword));
    }

    /** GET /api/counter-sales/combos: lấy combo đang mở bán để nhân viên bán kèm bắp nước. */
    @GetMapping("/combos")
    public ResponseEntity<?> combos() {
        return handle(counterSaleService::getActiveCombos);
    }

    /** GET /api/counter-sales/vouchers: lấy voucher còn hạn/còn số lượng để nhân viên áp dụng tại quầy. */
    @GetMapping("/vouchers")
    public ResponseEntity<?> vouchers() {
        return handle(counterSaleService::getActiveVouchers);
    }

    /**
     * GET /api/counter-sales/seats
     *
     * Dùng sau khi chọn suất chiếu.
     * `showtimeId` xác định phòng/sơ đồ ghế; `holdToken` giúp API đánh dấu ghế của phiên hiện tại là SELECTED
     * thay vì nhầm là ghế đang bị người khác giữ.
     */
    @GetMapping("/seats")
    public ResponseEntity<?> seats(@RequestParam("showtimeId") Long showtimeId,
                                   @RequestParam(value = "holdToken", required = false) String holdToken) {
        return handle(() -> counterSaleService.getSeatMap(showtimeId, holdToken));
    }

    /**
     * POST /api/counter-sales/hold
     *
     * Dùng khi nhân viên chọn hoặc đổi ghế ở bước 1.
     * Service sẽ tạo/cập nhật `holdToken`, ghi các ghế đang giữ vào `booking_tickets`
     * với status HOLDING và thời hạn 5 phút.
     */
    @PostMapping("/hold")
    public ResponseEntity<?> hold(@RequestBody CounterSaleService.HoldRequest request) {
        return handle(() -> counterSaleService.holdSeats(request));
    }

    /**
     * POST /api/counter-sales/release
     *
     * Dùng khi nhân viên bấm đổi ghế, quay lại bước 1, hoặc rời màn checkout.
     * Chỉ xóa các ghế HOLDING chưa gắn booking, tránh giữ ghế ảo trên một máy POS.
     */
    @PostMapping("/release")
    public ResponseEntity<?> release(@RequestBody Map<String, String> payload) {
        return handle(() -> {
            counterSaleService.releaseHold(payload.get("holdToken"));
            return Map.of("success", true);
        });
    }

    /**
     * POST /api/counter-sales/preview
     *
     * Tính hóa đơn nháp:
     * - Lấy ghế đang HOLDING theo token.
     * - Tính giá vé theo loại khách/loại ghế/suất chiếu.
     * - Cộng combo.
     * - Kiểm tra voucher ở chế độ không strict để trả lý do nếu voucher chưa hợp lệ.
     */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody CounterSaleService.CounterSaleRequest request) {
        return handle(() -> counterSaleService.previewSale(request));
    }

    /**
     * POST /api/counter-sales/complete
     *
     * Chốt đơn tiền mặt tại quầy:
     * - Booking chuyển PAID.
     * - Ghế HOLDING chuyển BOOKED.
     * - Payment tạo status SUCCESS, method CASH.
     * - Tạo bản ghi vé hiển thị/in được.
     */
    @PostMapping("/complete")
    public ResponseEntity<?> complete(@RequestBody CounterSaleService.CounterSaleRequest request) {
        return handle(() -> counterSaleService.completeSale(request));
    }

    /**
     * POST /api/counter-sales/payment-link
     *
     * Tạo đơn thanh toán payOS tại quầy:
     * - Booking tạo status PENDING.
     * - Payment tạo status PENDING, method PAYOS.
     * - Trả checkoutUrl để frontend chuyển thẳng sang trang thanh toán payOS.
     */
    @PostMapping("/payment-link")
    public ResponseEntity<?> paymentLink(@RequestBody CounterSaleService.CounterSaleRequest request,
                                         HttpServletRequest httpRequest) {
        return handle(() -> counterSaleService.createCounterPayment(request, httpRequest));
    }

    /**
     * Wrapper chuẩn hóa lỗi API.
     *
     * `IllegalArgumentException`/`IllegalStateException` là lỗi nghiệp vụ trả 400 để frontend hiển thị cho nhân viên.
     * Các lỗi còn lại là lỗi hệ thống trả 500.
     */
    private ResponseEntity<?> handle(Action action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi hệ thống: " + ex.getMessage(), "message", "Lỗi hệ thống: " + ex.getMessage()));
        }
    }

    private interface Action {
        Object run();
    }
}
