package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.service.TicketService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Controller xử lý hiển thị danh sách vé và chi tiết vé của người dùng đã đăng nhập.
 *
 * Ngày thực hiện: 26/06/2026
 */
@Controller
// Tat ca endpoint trong controller nay co tien to /my-tickets.
@RequestMapping("/my-tickets")
public class TicketController {

    // Moi trang danh sach chi hien toi da 5 ban ghi.
    private static final int DEFAULT_PAGE_SIZE = 5;

    // Service doc danh sach va chi tiet ve da phat hanh.
    @Autowired
    private TicketService ticketService;
    
    // Service tong hop Booking va Payment thanh lich su giao dich.
    @Autowired
    private com.group3.cinema.service.PaymentService paymentService;

    /**
     * Hiển thị danh sách vé của người dùng đang đăng nhập.
     * GET /my-tickets
     */
    // GET /my-tickets: hien danh sach ve cua account dang dang nhap.
    @GetMapping
    public String viewMyTickets(
            // Query param page; neu URL khong truyen thi bat dau tu trang 1.
            @RequestParam(value = "page", defaultValue = "1") int page,
            // Session chua loggedInUser.
            HttpSession session,
            // Model mang du lieu phan trang sang Thymeleaf.
            Model model) {
        // Lay account dang dang nhap tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Chi user da dang nhap moi duoc xem ve cua minh.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Query tat ca ve thuoc dung account, moi nhat truoc.
        List<Ticket> allTickets = ticketService.getTicketsByAccount(loggedInUser.getAccountID());
        // Dem tong so ve de hien thong ke va tinh so trang.
        int totalTickets = allTickets.size();
        // Chia tong ve cho page size; Math.max giu it nhat mot trang.
        int totalPages = Math.max(1, (int) Math.ceil((double) totalTickets / DEFAULT_PAGE_SIZE));
        // Ep page vao khoang hop le tu 1 den totalPages.
        int currentPage = Math.max(1, Math.min(page, totalPages));
        // Tinh vi tri bat dau cua trang trong List, khong vuot qua size.
        int fromIndex = Math.min((currentPage - 1) * DEFAULT_PAGE_SIZE, totalTickets);
        // Tinh vi tri ket thuc khong vuot qua tong so ve.
        int toIndex = Math.min(fromIndex + DEFAULT_PAGE_SIZE, totalTickets);
        // Danh sach rong dung Collections.emptyList; neu co du lieu thi cat subList.
        List<Ticket> tickets = totalTickets == 0 ? Collections.emptyList() : allTickets.subList(fromIndex, toIndex);

        // Danh sach ve cua trang hien tai.
        model.addAttribute("tickets", tickets);
        // Tong so ve cua account.
        model.addAttribute("totalTickets", totalTickets);
        // Trang dang hien thi.
        model.addAttribute("currentPage", currentPage);
        // Tong so trang.
        model.addAttribute("totalPages", totalPages);
        // So ban ghi toi da tren mot trang.
        model.addAttribute("pageSize", DEFAULT_PAGE_SIZE);
        // Account dang dang nhap cho header/view.
        model.addAttribute("user", loggedInUser);
        // Render src/main/resources/templates/my-tickets.html.
        return "my-tickets";
    }

    /**
     * Hiển thị chi tiết một vé cụ thể.
     * GET /my-tickets/{id}
     * Chỉ cho phép xem vé của chính mình (bảo mật).
     */
    // GET /my-tickets/{id}: xem chi tiet mot ve va ma QR cua ve do.
    @GetMapping("/{id}")
    public String viewTicketDetail(
            // Lay id ve tu phan {id} tren URL.
            @PathVariable("id") Long id,
            // Session xac dinh account dang xem.
            HttpSession session,
            // Model mang Ticket sang trang chi tiet.
            Model model) {
        // Lay account dang dang nhap.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Khong cho xem ve khi chua dang nhap.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Query dong thoi theo ticketId va accountId de ngan xem ve cua nguoi khac.
        Optional<Ticket> ticketOpt = ticketService.getTicketDetail(id, loggedInUser.getAccountID());
        // Optional rong khi ve khong ton tai hoac khong thuoc account hien tai.
        if (ticketOpt.isEmpty()) {
            return "redirect:/my-tickets";
        }

        // Dua Ticket hop le sang template.
        model.addAttribute("ticket", ticketOpt.get());
        // Dua user sang view cho header.
        model.addAttribute("user", loggedInUser);
        // Render src/main/resources/templates/ticket-detail.html.
        return "ticket-detail";
    }

    /**
     * Hiển thị trang Lịch sử giao dịch (Booking History)
     * GET /my-tickets/booking-history
     */
    // GET /my-tickets/booking-history: hien lich su dat ve/thanh toan.
    @GetMapping("/booking-history")
    public String viewBookingHistory(
            // Query param page; mac dinh trang 1.
            @RequestParam(value = "page", defaultValue = "1") int page,
            // Session xac dinh account so huu giao dich.
            HttpSession session,
            // Model mang danh sach DTO va thong tin phan trang sang view.
            Model model) {
        // Lay account dang dang nhap.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Chuyen ve login neu chua co session.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Service ghep Booking, Payment, Showtime va ghe thanh cac DTO de hien thi.
        List<com.group3.cinema.dto.BookingHistoryDto> allHistory = paymentService.getBookingHistory(loggedInUser.getAccountID());
        // Tong so giao dich cua account.
        int totalHistory = allHistory.size();
        // Tinh tong trang, toi thieu mot trang.
        int totalPages = Math.max(1, (int) Math.ceil((double) totalHistory / DEFAULT_PAGE_SIZE));
        // Chuan hoa page vao khoang hop le.
        int currentPage = Math.max(1, Math.min(page, totalPages));
        // Chi so dau cua trang trong danh sach.
        int fromIndex = Math.min((currentPage - 1) * DEFAULT_PAGE_SIZE, totalHistory);
        // Chi so cuoi, khong vuot qua size.
        int toIndex = Math.min(fromIndex + DEFAULT_PAGE_SIZE, totalHistory);
        // Cat danh sach cua trang hien tai hoac tra danh sach rong.
        List<com.group3.cinema.dto.BookingHistoryDto> history =
                totalHistory == 0 ? Collections.emptyList() : allHistory.subList(fromIndex, toIndex);

        // Danh sach giao dich cua trang hien tai.
        model.addAttribute("history", history);
        // Tong so giao dich.
        model.addAttribute("totalHistory", totalHistory);
        // Trang hien tai.
        model.addAttribute("currentPage", currentPage);
        // Tong so trang.
        model.addAttribute("totalPages", totalPages);
        // So giao dich toi da moi trang.
        model.addAttribute("pageSize", DEFAULT_PAGE_SIZE);
        // Account dang dang nhap cho view/header.
        model.addAttribute("user", loggedInUser);
        // Render src/main/resources/templates/booking-history.html.
        return "booking-history";
    }
}
