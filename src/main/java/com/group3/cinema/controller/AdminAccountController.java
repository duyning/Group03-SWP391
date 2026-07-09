package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import com.group3.cinema.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller cho chức năng Quản lý tài khoản (Manage Accounts).
 * Dành riêng cho ADMIN: xem danh sách, tìm kiếm, vô hiệu hóa / kích hoạt.
 *
 * Ngày thực hiện: 09/07/2026
 * Tạo bởi: DuongND_HE186619
 */
@Controller
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    @Autowired
    private AccountService accountService;

    /**
     * GET /admin/accounts
     * Hiển thị danh sách tất cả tài khoản, hỗ trợ lọc bằng query param.
     */
    @GetMapping
    public String listAccounts(
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "role", required = false, defaultValue = "") String roleFilter,
            @RequestParam(value = "status", required = false, defaultValue = "") String statusFilter,
            HttpSession session, Model model) {

        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) return "redirect:/login";
        if (loggedInUser.getRole() != Role.ADMIN) {
            return "redirect:/admin/dashboard";
        }

        List<Account> accounts = accountService.getAllAccounts();

        // Apply search filter
        if (!search.isBlank()) {
            String q = search.toLowerCase();
            accounts = accounts.stream()
                    .filter(a -> (a.getName() != null && a.getName().toLowerCase().contains(q))
                            || (a.getEmail() != null && a.getEmail().toLowerCase().contains(q))
                            || (a.getPhoneNum() != null && a.getPhoneNum().contains(q)))
                    .collect(Collectors.toList());
        }

        // Apply role filter
        if (!roleFilter.isBlank()) {
            try {
                Role r = Role.valueOf(roleFilter.toUpperCase());
                accounts = accounts.stream()
                        .filter(a -> a.getRole() == r)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) { }
        }

        // Apply status filter
        if ("active".equalsIgnoreCase(statusFilter)) {
            accounts = accounts.stream().filter(Account::isStatus).collect(Collectors.toList());
        } else if ("inactive".equalsIgnoreCase(statusFilter)) {
            accounts = accounts.stream().filter(a -> !a.isStatus()).collect(Collectors.toList());
        }

        model.addAttribute("accounts", accounts);
        model.addAttribute("search", search);
        model.addAttribute("roleFilter", roleFilter);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("totalCount", accounts.size());
        model.addAttribute("activeCount", accounts.stream().filter(Account::isStatus).count());
        model.addAttribute("inactiveCount", accounts.stream().filter(a -> !a.isStatus()).count());
        model.addAttribute("user", loggedInUser);
        model.addAttribute("active", "accounts");
        return "admin-account-list";
    }

    /**
     * POST /admin/accounts/{id}/toggle
     * Vô hiệu hóa hoặc kích hoạt tài khoản.
     */
    @PostMapping("/{id}/toggle")
    public String toggleStatus(
            @PathVariable("id") int targetId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) return "redirect:/login";
        if (loggedInUser.getRole() != Role.ADMIN) {
            return "redirect:/admin/dashboard";
        }

        try {
            Account updated = accountService.toggleAccountStatus(targetId, loggedInUser.getAccountID());
            String action = updated.isStatus() ? "kích hoạt" : "vô hiệu hóa";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã " + action + " tài khoản \"" + updated.getName() + "\" thành công.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/accounts";
    }
}
