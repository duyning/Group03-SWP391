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
@RequestMapping("/my-tickets")
public class TicketController {

    private static final int DEFAULT_PAGE_SIZE = 5;

    @Autowired
    private TicketService ticketService;
    
    @Autowired
    private com.group3.cinema.service.PaymentService paymentService;

    /**
     * Hiển thị danh sách vé của người dùng đang đăng nhập.
     * GET /my-tickets
     */
    @GetMapping
    public String viewMyTickets(
            @RequestParam(value = "page", defaultValue = "1") int page,
            HttpSession session,
            Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        List<Ticket> allTickets = ticketService.getTicketsByAccount(loggedInUser.getAccountID());
        int totalTickets = allTickets.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalTickets / DEFAULT_PAGE_SIZE));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.min((currentPage - 1) * DEFAULT_PAGE_SIZE, totalTickets);
        int toIndex = Math.min(fromIndex + DEFAULT_PAGE_SIZE, totalTickets);
        List<Ticket> tickets = totalTickets == 0 ? Collections.emptyList() : allTickets.subList(fromIndex, toIndex);

        model.addAttribute("tickets", tickets);
        model.addAttribute("totalTickets", totalTickets);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", DEFAULT_PAGE_SIZE);
        model.addAttribute("user", loggedInUser);
        return "my-tickets";
    }

    /**
     * Hiển thị chi tiết một vé cụ thể.
     * GET /my-tickets/{id}
     * Chỉ cho phép xem vé của chính mình (bảo mật).
     */
    @GetMapping("/{id}")
    public String viewTicketDetail(@PathVariable("id") Long id, HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Optional<Ticket> ticketOpt = ticketService.getTicketDetail(id, loggedInUser.getAccountID());
        if (ticketOpt.isEmpty()) {
            return "redirect:/my-tickets";
        }

        model.addAttribute("ticket", ticketOpt.get());
        model.addAttribute("user", loggedInUser);
        return "ticket-detail";
    }

    /**
     * Hiển thị trang Lịch sử giao dịch (Booking History)
     * GET /my-tickets/booking-history
     */
    @GetMapping("/booking-history")
    public String viewBookingHistory(
            @RequestParam(value = "page", defaultValue = "1") int page,
            HttpSession session,
            Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        List<com.group3.cinema.dto.BookingHistoryDto> allHistory = paymentService.getBookingHistory(loggedInUser.getAccountID());
        int totalHistory = allHistory.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalHistory / DEFAULT_PAGE_SIZE));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.min((currentPage - 1) * DEFAULT_PAGE_SIZE, totalHistory);
        int toIndex = Math.min(fromIndex + DEFAULT_PAGE_SIZE, totalHistory);
        List<com.group3.cinema.dto.BookingHistoryDto> history =
                totalHistory == 0 ? Collections.emptyList() : allHistory.subList(fromIndex, toIndex);

        model.addAttribute("history", history);
        model.addAttribute("totalHistory", totalHistory);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", DEFAULT_PAGE_SIZE);
        model.addAttribute("user", loggedInUser);
        return "booking-history";
    }
}
