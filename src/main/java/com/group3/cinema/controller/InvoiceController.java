package com.group3.cinema.controller;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.service.InvoiceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * LUỒNG FRONT-END -> BACK-END: MÀN QUẢN LÝ HÓA ĐƠN
     *
     * Front-end:
     * - `invoice-list.html` form lọc dùng method GET, action `/admin/invoices`.
     * - Các input `keyword`, `bookingStatus`, `paymentStatus`, `paymentMethod`, `fromDate`, `toDate`,
     *   `page`, `size` được trình duyệt gửi lên dưới dạng query string.
     *
     * Controller:
     * - Hàm này nhận query string, gọi `buildFilter(...)` để gom về `InvoiceService.InvoiceFilter`.
     * - `buildFilter(...)` cũng chuẩn hóa phương thức thanh toán: nghiệp vụ hiện chỉ quản lý `CASH` và `PAYOS`.
     *
     * Service:
     * - `invoiceService.searchInvoices(filter)` truy vấn Booking/Payment/Ticket, lọc hóa đơn thật,
     *   sau đó gom nhóm theo suất chiếu để view không phải hiển thị một bảng dài rối mắt.
     *
     * View:
     * - Controller đưa `filter`, `page`, `paymentStatuses`, `paymentMethods` vào Model.
     * - Thymeleaf render lại `invoice-list.html`, giữ nguyên bộ lọc đã chọn và danh sách hóa đơn theo nhóm suất chiếu.
     */
    @GetMapping("/admin/invoices")
    public String invoices(@RequestParam(value = "keyword", required = false) String keyword,
                           @RequestParam(value = "bookingStatus", required = false) Booking.Status bookingStatus,
                           @RequestParam(value = "paymentStatus", required = false) Payment.Status paymentStatus,
                           @RequestParam(value = "paymentMethod", required = false) Payment.Method paymentMethod,
                           @RequestParam(value = "fromDate", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                           @RequestParam(value = "toDate", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                           @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                           @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
                           Model model) {
        InvoiceService.InvoiceFilter filter = buildFilter(keyword, bookingStatus, paymentStatus, paymentMethod, fromDate, toDate, page, size);
        model.addAttribute("filter", filter);
        model.addAttribute("page", invoiceService.searchInvoices(filter));
        model.addAttribute("paymentStatuses", Payment.Status.values());
        model.addAttribute("paymentMethods", List.of(Payment.Method.CASH, Payment.Method.PAYOS));
        return "invoice-list";
    }

    /**
     * LUỒNG NÚT "Xuất CSV" TRÊN `invoice-list.html`
     *
     * Front-end:
     * - Link `Xuất CSV` trỏ tới `/admin/invoices/export` và truyền lại toàn bộ điều kiện lọc hiện tại.
     *
     * Controller:
     * - Hàm này build cùng một `InvoiceFilter` như màn danh sách, nhưng không trả view.
     *
     * Service:
     * - `invoiceService.exportInvoicesCsv(filter)` lấy dữ liệu theo bộ lọc và dựng file CSV UTF-8 có BOM,
     *   giúp Excel đọc tiếng Việt không lỗi font.
     *
     * Response:
     * - Trả `ResponseEntity<byte[]>` với header `Content-Disposition: attachment`,
     *   trình duyệt sẽ tải file `hoa-don-yyyyMMdd.csv`.
     */
    @GetMapping("/admin/invoices/export")
    public ResponseEntity<byte[]> exportInvoices(@RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "bookingStatus", required = false) Booking.Status bookingStatus,
                                                 @RequestParam(value = "paymentStatus", required = false) Payment.Status paymentStatus,
                                                 @RequestParam(value = "paymentMethod", required = false) Payment.Method paymentMethod,
                                                 @RequestParam(value = "fromDate", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                 @RequestParam(value = "toDate", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        InvoiceService.InvoiceFilter filter = buildFilter(keyword, bookingStatus, paymentStatus, paymentMethod, fromDate, toDate, 1, 100);
        byte[] content = invoiceService.exportInvoicesCsv(filter);
        String fileName = "hoa-don-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(content);
    }

    /**
     * LUỒNG NÚT "Xem chi tiết" TRONG TỪNG HÓA ĐƠN
     *
     * Front-end:
     * - Icon mắt trong `invoice-list.html` trỏ tới `/admin/invoices/{bookingId}`.
     *
     * Controller:
     * - Gọi `invoiceService.getInvoiceDetails(bookingId)`.
     *
     * Service:
     * - Lấy Booking, Account, Showtime, BookingTicket, BookingCombo, BookingFoodItem và Payment mới nhất.
     * - Gom thành `InvoiceDetails` để view chi tiết có đầy đủ thông tin hóa đơn, vé, combo và giao dịch.
     */
    @GetMapping("/admin/invoices/{bookingId}")
    public String invoiceDetails(@PathVariable Long bookingId, Model model,
                                 RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("details", invoiceService.getInvoiceDetails(bookingId));
            return "invoice-detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/invoices";
        }
    }

    /**
     * LUỒNG NÚT "In / Xuất PDF"
     *
     * Front-end:
     * - Icon file PDF ở danh sách hoặc nút in ở chi tiết mở `/admin/invoices/{bookingId}/print` trong tab mới.
     *
     * Controller/Service:
     * - Dùng lại `invoiceService.getInvoiceDetails(bookingId)` để đảm bảo bản in và bản chi tiết cùng nguồn dữ liệu.
     *
     * View:
     * - Trả `invoice-print.html`; trình duyệt có thể in hoặc lưu PDF.
     */
    @GetMapping("/admin/invoices/{bookingId}/print")
    public String invoicePrint(@PathVariable Long bookingId, Model model,
                               RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("details", invoiceService.getInvoiceDetails(bookingId));
            return "invoice-print";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/invoices";
        }
    }

    /**
     * LUỒNG FORM "HỦY HÓA ĐƠN CHỜ THANH TOÁN"
     *
     * Front-end:
     * - `invoice-detail.html` submit POST `/admin/invoices/{bookingId}/cancel`, kèm `reason` nếu quản lý nhập lý do.
     *
     * Service:
     * - `cancelPendingInvoice(...)` chỉ cho hủy Booking trạng thái `PENDING`.
     * - Nếu hợp lệ: Booking -> CANCELLED, xóa BookingTicket giữ tạm, Payment pending -> CANCELLED.
     *
     * Sau xử lý:
     * - Redirect lại trang chi tiết để quản lý thấy flash message thành công/lỗi.
     */
    @PostMapping("/admin/invoices/{bookingId}/cancel")
    public String cancelInvoice(@PathVariable Long bookingId,
                                @RequestParam(value = "reason", required = false) String reason,
                                RedirectAttributes redirectAttributes) {
        try {
            invoiceService.cancelPendingInvoice(bookingId, reason);
            redirectAttributes.addFlashAttribute("success", "Đã hủy hóa đơn chờ thanh toán và giải phóng ghế giữ tạm.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/invoices/" + bookingId;
    }

    /**
     * LUỒNG FORM "HOÀN TIỀN HÓA ĐƠN ĐÃ THANH TOÁN"
     *
     * Front-end:
     * - `invoice-detail.html` submit POST `/admin/invoices/{bookingId}/refund`, có thể kèm lý do hoàn tiền.
     *
     * Service:
     * - `refundPaidInvoice(...)` chỉ cho xử lý Booking đã PAID và có Payment SUCCESS.
     * - Booking chuyển CANCELLED, Payment chuyển CANCELLED với responseCode `REFUNDED`.
     * - Hệ thống giữ lịch sử thay vì xóa hóa đơn để phục vụ đối soát sau này.
     */
    @PostMapping("/admin/invoices/{bookingId}/refund")
    public String refundInvoice(@PathVariable Long bookingId,
                                @RequestParam(value = "reason", required = false) String reason,
                                RedirectAttributes redirectAttributes) {
        try {
            invoiceService.refundPaidInvoice(bookingId, reason);
            redirectAttributes.addFlashAttribute("success", "Đã ghi nhận hoàn tiền và hủy hiệu lực hóa đơn.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/invoices/" + bookingId;
    }

    private InvoiceService.InvoiceFilter buildFilter(String keyword,
                                                     Booking.Status bookingStatus,
                                                     Payment.Status paymentStatus,
                                                     Payment.Method paymentMethod,
                                                     LocalDate fromDate,
                                                     LocalDate toDate,
                                                     Integer page,
                                                     Integer size) {
        Payment.Method normalizedPaymentMethod = paymentMethod == Payment.Method.CASH || paymentMethod == Payment.Method.PAYOS
                ? paymentMethod
                : null;
        return new InvoiceService.InvoiceFilter(keyword, bookingStatus, paymentStatus, normalizedPaymentMethod, fromDate, toDate, page, size);
    }
}
