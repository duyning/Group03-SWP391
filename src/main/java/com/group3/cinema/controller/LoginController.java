package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import com.group3.cinema.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xá»­ lÃ½ tÃ­nh nÄƒng Ä‘Äƒng nháº­p vÃ  Ä‘Äƒng xuáº¥t (Login/Logout).
 * XÃ¡c thá»±c thÃ´ng tin ngÆ°á»i dÃ¹ng tá»« cÆ¡ sá»Ÿ dá»¯ liá»‡u vÃ  quáº£n lÃ½ tráº¡ng thÃ¡i phiÃªn lÃ m viá»‡c (Session).
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
@Controller
public class LoginController {

    @Autowired
    private AccountService accountService;

    /**
     * Show the login form.
     */
    @GetMapping("/login")
    public String showLoginForm(HttpSession session, Model model) {
        // If already logged in, redirect to home
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/home";
        }
        return "login";
    }

    /**
     * Process login form submission.
     * Validates email + password, stores account in session on success.
     */
    @PostMapping("/login")
    public String processLogin(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Account account = accountService.login(email, password);

        if (account == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email hoặc mật khẩu không đúng");
            return "redirect:/login?error";
        }

        if (!account.isStatus()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản của bạn đã bị khóa");
            return "redirect:/login?error";
        }

        // Save logged-in user to session
        session.setAttribute("loggedInUser", account);

        // Phân luồng redirect theo vai trò
        if (account.getRole() == Role.ADMIN || account.getRole() == Role.MANAGER) {
            return "redirect:/admin/dashboard";
        }

        Object redirectTarget = session.getAttribute("redirectAfterLogin");
        session.removeAttribute("redirectAfterLogin");
        if (redirectTarget instanceof String target
                && target.startsWith("/")
                && !target.startsWith("//")) {
            return "redirect:" + target;
        }
        return "redirect:/home";
    }

    /**
     * Logout: invalidate session and redirect to login.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("successMessage", "Đăng xuất thành công!");
        return "redirect:/login?logout";
    }
}
