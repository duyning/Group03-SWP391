package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller xử lý tính năng đổi mật khẩu (Reset Password).
 */
@Controller
public class ResetPasswordController {

    @Autowired
    private AccountService accountService;

    @GetMapping("/reset-password")
    public String showResetPasswordForm(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("oldPassword", "");
        model.addAttribute("newPassword", "");
        model.addAttribute("confirmPassword", "");
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam(value = "oldPassword", required = false) String oldPassword,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            HttpSession session,
            Model model) {

        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Account account = accountService.findById(loggedInUser.getAccountID());
        if (account == null) {
            model.addAttribute("formError", "Tài khoản không tồn tại.");
            return prepareForm(model, oldPassword, newPassword, confirmPassword);
        }

        try {
            accountService.resetPassword(account, oldPassword, newPassword, confirmPassword);
            session.setAttribute("loggedInUser", account);
            model.addAttribute("successMessage", "Đổi mật khẩu thành công!");
            return prepareForm(model, "", "", "");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if ("Old password is incorrect".equals(message)) {
                model.addAttribute("oldPasswordError", "Mật khẩu cũ không đúng.");
            } else if (message != null && !message.isBlank()) {
                model.addAttribute("formError", message);
            }
            return prepareForm(model, oldPassword, newPassword, confirmPassword);
        }
    }

    private String prepareForm(Model model, String oldPassword, String newPassword, String confirmPassword) {
        model.addAttribute("oldPassword", oldPassword == null ? "" : oldPassword);
        model.addAttribute("newPassword", newPassword == null ? "" : newPassword);
        model.addAttribute("confirmPassword", confirmPassword == null ? "" : confirmPassword);
        return "reset-password";
    }
}
