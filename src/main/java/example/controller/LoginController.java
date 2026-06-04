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
