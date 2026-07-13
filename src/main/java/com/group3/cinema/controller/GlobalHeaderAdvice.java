package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalHeaderAdvice {

    private final NotificationService notificationService;

    public GlobalHeaderAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void addHeaderContext(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            model.addAttribute("unreadNotificationCount", 0);
            return;
        }

        if (!model.containsAttribute("user")) {
            model.addAttribute("user", loggedInUser);
        }

        int unreadNotificationCount = 0;
        try {
            unreadNotificationCount = notificationService.getUnreadCount(loggedInUser.getAccountID());
        } catch (RuntimeException exception) {
            System.err.println("Khong the lay so thong bao chua doc: " + exception.getMessage());
        }
        model.addAttribute("unreadNotificationCount", unreadNotificationCount);
    }
}
