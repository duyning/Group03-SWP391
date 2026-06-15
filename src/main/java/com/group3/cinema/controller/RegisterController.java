package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.AccountService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xá»­ lÃ½ tÃ­nh nÄƒng Ä‘Äƒng kÃ½ tÃ i khoáº£n (Register).
 * Kiá»ƒm tra tÃ­nh há»£p lá»‡ dá»¯ liá»‡u Ä‘áº§u vÃ o vÃ  kiá»ƒm tra trÃ¹ng láº·p email/sá»‘ Ä‘iá»‡n thoáº¡i trÆ°á»›c khi Ä‘Äƒng kÃ½.
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
@Controller
public class RegisterController {

    private static final String PENDING_REGISTER_ACCOUNT = "pendingRegisterAccount";
    private static final String PENDING_REGISTER_OTP = "pendingRegisterOtp";
    private static final String PENDING_REGISTER_EMAIL = "pendingRegisterEmail";

    @Autowired
    private AccountService accountService;

    /**
     * Show the registration form.
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model, HttpSession session) {
        session.removeAttribute(PENDING_REGISTER_ACCOUNT);
        session.removeAttribute(PENDING_REGISTER_OTP);
        session.removeAttribute(PENDING_REGISTER_EMAIL);
        model.addAttribute("account", new Account());
        return "register";
    }

    /**
     * Process the registration form submission.
     * Validates input, checks for duplicate email/phone, and saves the account.
     */
    @PostMapping("/register")
    public String processRegister(
            @Valid @ModelAttribute("account") Account account,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        // Check for duplicate email
        if (accountService.isEmailExist(account.getEmail())) {
            bindingResult.rejectValue("email", "error.account", "Email đã được sử dụng");
        }

        // Check for duplicate phone number
        if (account.getPhoneNum() != null && !account.getPhoneNum().isEmpty()
                && accountService.isPhoneNumExist(account.getPhoneNum())) {
            bindingResult.rejectValue("phoneNum", "error.account", "Số điện thoại đã được sử dụng");
        }

        // Map backend age validation to the 'dob' field so the UI can display it
        if (account.getDob() != null && !account.isValidAge()) {
            bindingResult.rejectValue("dob", "error.account", "Tuổi không hợp lệ (phải từ 13 đến 100 tuổi).");
        }

        // If validation errors exist, return back to the form
        if (bindingResult.hasErrors()) {
            return "register";
        }

        // Create OTP and keep the account temporarily in session until verification completes
        String otp = accountService.generateAndSendRegisterOTP(account.getEmail());
        session.setAttribute(PENDING_REGISTER_ACCOUNT, account);
        session.setAttribute(PENDING_REGISTER_OTP, otp);
        session.setAttribute(PENDING_REGISTER_EMAIL, account.getEmail());

        redirectAttributes.addFlashAttribute("successMessage",
                "Mã OTP đã được gửi đến email của bạn. Vui lòng nhập mã để hoàn tất đăng ký.");
        return "redirect:/register/otp";
    }

    @GetMapping("/register/otp")
    public String showRegisterOtpForm(HttpSession session, Model model) {
        Account pendingAccount = (Account) session.getAttribute(PENDING_REGISTER_ACCOUNT);
        String email = (String) session.getAttribute(PENDING_REGISTER_EMAIL);

        if (pendingAccount == null || email == null) {
            return "redirect:/register";
        }

        model.addAttribute("email", email);
        return "register-otp";
    }

    @PostMapping("/register/otp")
    public String verifyRegisterOtp(
            @RequestParam("otp") String userOtp,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String sessionOtp = (String) session.getAttribute(PENDING_REGISTER_OTP);
        Account pendingAccount = (Account) session.getAttribute(PENDING_REGISTER_ACCOUNT);

        if (pendingAccount == null || sessionOtp == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phiên đăng ký đã hết hạn. Vui lòng đăng ký lại.");
            return "redirect:/register";
        }

        if (!sessionOtp.equals(userOtp)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã OTP không đúng. Vui lòng thử lại.");
            return "redirect:/register/otp";
        }

        accountService.register(pendingAccount);

        session.removeAttribute(PENDING_REGISTER_ACCOUNT);
        session.removeAttribute(PENDING_REGISTER_OTP);
        session.removeAttribute(PENDING_REGISTER_EMAIL);

        redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
        return "redirect:/register?success";
    }

    @GetMapping("/register/resend-otp")
    public String resendRegisterOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        Account pendingAccount = (Account) session.getAttribute(PENDING_REGISTER_ACCOUNT);
        if (pendingAccount == null) {
            return "redirect:/register";
        }

        String otp = accountService.generateAndSendRegisterOTP(pendingAccount.getEmail());
        session.setAttribute(PENDING_REGISTER_OTP, otp);

        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lại mã OTP đến email của bạn.");
        return "redirect:/register/otp";
    }
}
