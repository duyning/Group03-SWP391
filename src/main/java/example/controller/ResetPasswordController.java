package example.controller;

import example.entity.Account;
import example.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xử lý tính năng đổi mật khẩu (Reset Password).
 * Yêu cầu người dùng đăng nhập và kiểm tra mật khẩu cũ, mật khẩu mới, xác nhận mật khẩu trước khi cập nhật.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Controller
public class ResetPasswordController {

    @Autowired
    private AccountService accountService;

    /**
     * Show the reset password form.
     * Requires the user to be logged in (session contains loggedInUser).
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        return "reset-password";
    }

    /**
     * Process the reset password form.
     * Validates all 6 cases:
     *   1. Old password incorrect
     *   2. New password length invalid (8-20 chars)
     *   3. Confirm password mismatch
     *   4. New password same as old
     *   5. Success — update database
     *   6. Empty / null fields
     */
    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam(value = "oldPassword", required = false) String oldPassword,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Must be logged in
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Reload account fresh from DB to get current password
        Account account = accountService.findById(loggedInUser.getAccountID());
        if (account == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản không tồn tại.");
            return "redirect:/reset-password?error";
        }

        try {
            accountService.resetPassword(account, oldPassword, newPassword, confirmPassword);

            // Update session with new account info
            session.setAttribute("loggedInUser", account);

            redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công!");
            return "redirect:/reset-password?success";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reset-password?error";
        }
    }
}
