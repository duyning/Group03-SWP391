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
// Tat ca endpoint trong controller nay bat dau bang /admin/accounts.
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    // Service cung cap danh sach account va nghiep vu doi trang thai.
    @Autowired
    private AccountService accountService;

    /**
     * GET /admin/accounts
     * Hiển thị danh sách tất cả tài khoản, hỗ trợ lọc bằng query param.
     */
    // GET /admin/accounts: hien danh sach va xu ly cac bo loc tren URL.
    @GetMapping
    public String listAccounts(
            // Tu khoa tim theo tien to email; mac dinh rong.
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            // Role can loc: ADMIN, MANAGER, CUSTOMER hoac rong.
            @RequestParam(value = "role", required = false, defaultValue = "") String roleFilter,
            // Status can loc: active, inactive hoac rong.
            @RequestParam(value = "status", required = false, defaultValue = "") String statusFilter,
            // Session xac dinh admin; Model mang ket qua sang view.
            HttpSession session, Model model) {

        // Lay account dang dang nhap tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Neu chua dang nhap thi chuyen den login.
        if (loggedInUser == null) return "redirect:/login";
        // Kiem tra lai quyen ADMIN ngay tai controller.
        if (loggedInUser.getRole() != Role.ADMIN) {
            return "redirect:/admin/dashboard";
        }

        // Chuyen null thanh chuoi rong va bo khoang trang hai dau.
        String normalizedSearch = search == null ? "" : search.trim();
        // Lay tat ca account, sap xep theo ten.
        List<Account> accounts = accountService.getAllAccounts();

        // Apply search filter
        if (!normalizedSearch.isBlank()) {
            // Chuyen tu khoa ve chu thuong de so sanh khong phan biet hoa/thuong.
            String q = normalizedSearch.toLowerCase();
            // Stream giu lai account co email bat dau bang tu khoa.
            accounts = accounts.stream()
                    // Bo qua email null va so sanh startsWith.
                    .filter(a -> a.getEmail() != null && a.getEmail().toLowerCase().startsWith(q))
                    // Thu ket qua stream ve List moi.
                    .collect(Collectors.toList());
        }

        // Apply role filter
        if (!roleFilter.isBlank()) {
            try {
                // Chuyen chuoi query param thanh enum Role.
                Role r = Role.valueOf(roleFilter.toUpperCase());
                // Giu lai account co role trung bo loc.
                accounts = accounts.stream()
                        .filter(a -> a.getRole() == r)
                        .collect(Collectors.toList());
            // Role sai tren URL se duoc bo qua thay vi lam request loi.
            } catch (IllegalArgumentException ignored) { }
        }

        // Apply status filter
        if ("active".equalsIgnoreCase(statusFilter)) {
            // Giu lai account dang hoat dong.
            accounts = accounts.stream().filter(Account::isStatus).collect(Collectors.toList());
        } else if ("inactive".equalsIgnoreCase(statusFilter)) {
            // Giu lai account da bi vo hieu hoa.
            accounts = accounts.stream().filter(a -> !a.isStatus()).collect(Collectors.toList());
        }

        // Danh sach sau khi ap dung cac bo loc.
        model.addAttribute("accounts", accounts);
        // Giu tu khoa trong input sau request GET.
        model.addAttribute("search", normalizedSearch);
        // Giu role da chon trong select.
        model.addAttribute("roleFilter", roleFilter);
        // Giu status da chon trong select.
        model.addAttribute("statusFilter", statusFilter);
        // So ket qua sau loc.
        model.addAttribute("totalCount", accounts.size());
        // Dem so account active trong tap ket qua.
        model.addAttribute("activeCount", accounts.stream().filter(Account::isStatus).count());
        // Dem so account inactive trong tap ket qua.
        model.addAttribute("inactiveCount", accounts.stream().filter(a -> !a.isStatus()).count());
        // Admin dang dang nhap cho topbar.
        model.addAttribute("user", loggedInUser);
        // Danh dau muc accounts dang active trong sidebar.
        model.addAttribute("active", "accounts");
        // Render src/main/resources/templates/admin-account-list.html.
        return "admin-account-list";
    }

    /**
     * POST /admin/accounts/{id}/toggle
     * Vô hiệu hóa hoặc kích hoạt tài khoản.
     */
    // POST /admin/accounts/{id}/toggle: dao trang thai active/inactive.
    @PostMapping("/{id}/toggle")
    public String toggleStatus(
            // ID account muc tieu duoc lay tu URL.
            @PathVariable("id") int targetId,
            // Session xac dinh admin thuc hien thao tac.
            HttpSession session,
            // Flash message se duoc hien sau khi redirect.
            RedirectAttributes redirectAttributes) {

        // Lay account dang dang nhap.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Bat dang nhap neu session da het han.
        if (loggedInUser == null) return "redirect:/login";
        // Ngan role khac goi truc tiep endpoint POST.
        if (loggedInUser.getRole() != Role.ADMIN) {
            return "redirect:/admin/dashboard";
        }

        try {
            // Service kiem tra quy tac, dao status va save DB.
            Account updated = accountService.toggleAccountStatus(targetId, loggedInUser.getAccountID());
            // Chon dong tu thong bao theo trang thai sau cap nhat.
            String action = updated.isStatus() ? "kích hoạt" : "vô hiệu hóa";
            // Gui thong bao thanh cong qua request redirect.
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã " + action + " tài khoản \"" + updated.getName() + "\" thành công.");
        // Bat loi nghiep vu khi account khong ton tai hoac bi cam thay doi.
        } catch (IllegalArgumentException e) {
            // Hien message cua service tren trang danh sach.
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        // Quay lai danh sach de tai trang thai moi va tranh submit lai POST.
        return "redirect:/admin/accounts";
    }
}
