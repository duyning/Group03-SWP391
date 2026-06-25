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

    @Autowired
    private TicketService ticketService;

    /**
     * Hiển thị danh sách vé của người dùng đang đăng nhập.
     * GET /my-tickets
     */
    @GetMapping
    public String viewMyTickets(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        List<Ticket> tickets = ticketService.getTicketsByAccount(loggedInUser.getAccountID());
        model.addAttribute("tickets", tickets);
        model.addAttribute("user", loggedInUser);
        return "my-tickets";
    }

    /**
     * Hiển thị chi tiết một vé cụ thể.
     * GET /my-tickets/{id}
     * Chỉ cho phép xem vé của chính mình (bảo mật).
     */
    @GetMapping("/{id}")
    public String viewTicketDetail(@PathVariable Long id, HttpSession session, Model model) {
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
}
