package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.entity.Role;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
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

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping("/login")
    public String showLoginForm(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            return redirectByRole(loggedInUser);
        }
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("email") String email,
                               @RequestParam("password") String password,
                               HttpSession session,
                               HttpServletRequest request,
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

        session.setAttribute("loggedInUser", account);

        try {
            activityLogService.log(account.getAccountID(), ActionType.LOGIN, "Dang nhap he thong", request);
        } catch (RuntimeException exception) {
            System.err.println("Khong the ghi nhat ky dang nhap: " + exception.getMessage());
        }

        Object redirectTarget = session.getAttribute("redirectAfterLogin");
        session.removeAttribute("redirectAfterLogin");
        if (redirectTarget instanceof String target
                && target.startsWith("/")
                && !target.startsWith("//")) {
            return "redirect:" + target;
        }
        return redirectByRole(account);
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            try {
                activityLogService.log(loggedInUser.getAccountID(), ActionType.LOGOUT, "Dang xuat he thong");
            } catch (RuntimeException exception) {
                System.err.println("Khong the ghi nhat ky dang xuat: " + exception.getMessage());
            }
        }
        session.invalidate();
        redirectAttributes.addFlashAttribute("successMessage", "Đăng xuất thành công!");
        return "redirect:/login?logout";
    }

    private String redirectByRole(Account account) {
        if (account.getRole() == Role.ADMIN || account.getRole() == Role.MANAGER) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/home";
    }
}
