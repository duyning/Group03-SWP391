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
