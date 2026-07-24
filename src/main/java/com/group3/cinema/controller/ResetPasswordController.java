package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.ActivityLogService;
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

    @Autowired
    private ActivityLogService activityLogService;

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
            activityLogService.log(account.getAccountID(), ActionType.PASSWORD_CHANGE, "Đổi mật khẩu thành công");
            model.addAttribute("successMessage", "Đổi mật khẩu thành công!");
            return prepareForm(model, "", "", "");
        } catch (IllegalArgumentException e) {
            addPasswordFieldError(model, e.getMessage());
            return prepareForm(model, oldPassword, newPassword, confirmPassword);
        }
    }

    private String prepareForm(Model model, String oldPassword, String newPassword, String confirmPassword) {
        model.addAttribute("oldPassword", oldPassword == null ? "" : oldPassword);
        model.addAttribute("newPassword", newPassword == null ? "" : newPassword);
        model.addAttribute("confirmPassword", confirmPassword == null ? "" : confirmPassword);
        return "reset-password";
    }

    private void addPasswordFieldError(Model model, String message) {
        if (message == null || message.isBlank()) {
            model.addAttribute("formError", "Không đổi được mật khẩu. Vui lòng thử lại.");
            return;
        }

        switch (message) {
            case "Old password is incorrect", "Mật khẩu cũ không được để trống" ->
                    model.addAttribute("oldPasswordError", message.equals("Old password is incorrect")
                            ? "Mật khẩu cũ không đúng"
                            : message);
            case "Mật khẩu mới không được để trống", "New password must be different from old password", "New password must be 8-20 characters" ->
                    model.addAttribute("newPasswordError", switch (message) {
                        case "New password must be different from old password" -> "Mật khẩu mới phải khác mật khẩu cũ";
                        case "New password must be 8-20 characters" -> "Mật khẩu mới phải từ 8 đến 20 ký tự";
                        default -> message;
                    });
            case "Xác nhận mật khẩu không được để trống", "Confirm password does not match" ->
                    model.addAttribute("confirmPasswordError", message.equals("Confirm password does not match")
                            ? "Mật khẩu xác nhận không khớp"
                            : message);
            default -> model.addAttribute("formError", message);
        }
    }
}
