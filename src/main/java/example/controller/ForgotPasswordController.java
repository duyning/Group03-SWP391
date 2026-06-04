package example.controller;

import example.entity.Account;
import example.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xử lý tính năng quên mật khẩu (Forgot Password) bằng OTP gửi qua email.
 * Cho phép người dùng xác thực email, nhập mã OTP để xác nhận và thiết lập lại mật khẩu mới.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Controller
@RequestMapping("/forgot-password")
public class ForgotPasswordController {

    @Autowired
    private AccountService accountService;

    // Step 1: Show Email form
    @GetMapping
    public String showEmailForm(HttpSession session, Model model) {
        // Clear any previous forgot password session data to start fresh
        session.removeAttribute("forgotEmail");
        session.removeAttribute("forgotOtp");
        session.removeAttribute("otpVerified");
        
        model.addAttribute("step", 1);
        return "forgot-password";
    }

    // Step 1 Submit: Process Email, generate OTP
    @PostMapping("/send-otp")
    public String processEmail(@RequestParam("email") String email, HttpSession session, RedirectAttributes redirectAttributes) {
        Account account = accountService.findByEmail(email);
        if (account == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email không tồn tại trong hệ thống.");
            return "redirect:/forgot-password";
        }

        // Generate and "send" OTP
        String otp = accountService.generateAndSendOTP(email);
        
        // Save to session
        session.setAttribute("forgotEmail", email);
        session.setAttribute("forgotOtp", otp);
        
        redirectAttributes.addFlashAttribute("successMessage", "Mã xác nhận đã được gửi đến email " + email);
        return "redirect:/forgot-password/otp";
    }

    // Step 2: Show OTP form
    @GetMapping("/otp")
    public String showOtpForm(HttpSession session, Model model) {
        if (session.getAttribute("forgotEmail") == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("step", 2);
        return "forgot-password";
    }

    // Step 2 Submit: Verify OTP
    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("otp") String userOtp, HttpSession session, RedirectAttributes redirectAttributes) {
        String sessionOtp = (String) session.getAttribute("forgotOtp");
        
        if (sessionOtp == null || !sessionOtp.equals(userOtp)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã xác nhận không đúng hoặc đã hết hạn.");
            return "redirect:/forgot-password/otp";
        }

        // OTP verified successfully
        session.setAttribute("otpVerified", true);
        return "redirect:/forgot-password/new-password";
    }

    // Step 3: Show New Password form
    @GetMapping("/new-password")
    public String showNewPasswordForm(HttpSession session, Model model) {
        if (session.getAttribute("otpVerified") == null || !(boolean) session.getAttribute("otpVerified")) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("step", 3);
        return "forgot-password";
    }

    // Step 3 Submit: Update Password
    @PostMapping("/update-password")
    public String updatePassword(
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session, 
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("otpVerified") == null || !(boolean) session.getAttribute("otpVerified")) {
            return "redirect:/forgot-password";
        }

        String email = (String) session.getAttribute("forgotEmail");
        Account account = accountService.findByEmail(email);

        try {
            accountService.updatePassword(account, newPassword, confirmPassword);
            
            // Clear session
            session.removeAttribute("forgotEmail");
            session.removeAttribute("forgotOtp");
            session.removeAttribute("otpVerified");

            redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công! Vui lòng đăng nhập.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/forgot-password/new-password";
        }
    }
}
