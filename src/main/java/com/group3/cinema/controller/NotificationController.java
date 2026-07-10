package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 1. Hiển thị danh sách thông báo
    @GetMapping
    public String listNotifications(
            @RequestParam(value = "page", defaultValue = "0") int page,
            HttpSession session,
            Model model) {

        // Lấy account từ Session
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        int accountId = sessionAccount.getAccountID();

        // Lấy danh sách thông báo và đếm số chưa đọc
        model.addAttribute("notifications", notificationService.getUserNotifications(accountId, page, 10));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(accountId));

        return "notification-list";
    }

    // 2. Đánh dấu 1 thông báo là đã đọc
    @GetMapping("/read/{id}")
    public String readNotification(@PathVariable("id") Long id, HttpSession session) {
        // Lấy account từ Session để kiểm tra đăng nhập
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        notificationService.markAsRead(id);

        return "redirect:/notifications";
    }

    // 3. Đánh dấu tất cả là đã đọc
    @GetMapping("/read-all")
    public String readAllNotifications(HttpSession session, RedirectAttributes redirectAttributes) {
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        notificationService.markAllAsRead(sessionAccount.getAccountID());
        redirectAttributes.addFlashAttribute("message", "Đã đánh dấu tất cả thông báo là đã đọc.");

        return "redirect:/notifications";
    }
}