package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xử lý tính năng tạo tài khoản quản lý (Manager).
 * Dành riêng cho quyền Admin.
 */
@Controller
// Controller rieng cho flow Admin tao tai khoan co role MANAGER.
public class AdminManagerController {

    // Service kiem tra du lieu ton tai va luu Account moi.
    @Autowired
    private AccountService accountService;

    // GET /admin/create-manager: mo form tao manager.
    @GetMapping("/admin/create-manager")
    public String showCreateManagerForm(
            // Model chua Account rong de Thymeleaf binding form.
            Model model,
            // Session xac dinh user dang truy cap.
            HttpSession session,
            // RedirectAttributes mang thong bao loi quyen truy cap.
            RedirectAttributes redirectAttributes) {
        // Lay user dang dang nhap tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Chua dang nhap thi chuyen den login.
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        // Chi ADMIN moi duoc mo form tao manager.
        if (loggedInUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ Admin mới có quyền truy cập chức năng này!");
            return "redirect:/admin/dashboard";
        }
        // Tao Account rong lam form-backing object cho th:object.
        model.addAttribute("account", new Account());
        // Render src/main/resources/templates/create-manager-account.html.
        return "create-manager-account";
    }

    // POST /admin/create-manager: nhan form va tao manager moi.
    @PostMapping("/admin/create-manager")
    public String processCreateManager(
            // @Valid chay validation; @ModelAttribute bind input vao Account.
            @Valid @ModelAttribute("account") Account account,
            // Nhan toan bo loi validation cua Account.
            BindingResult bindingResult,
            // Model duoc dung khi render lai form co loi.
            Model model,
            // Mang thong bao qua redirect.
            RedirectAttributes redirectAttributes,
            // Session xac dinh admin dang thuc hien thao tac.
            HttpSession session) {

        // Lay account dang dang nhap.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Khong co session thi bat dang nhap.
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        // Kiem tra lai quyen ADMIN tai endpoint POST.
        if (loggedInUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ Admin mới có quyền thực hiện chức năng này!");
            return "redirect:/admin/dashboard";
        }

        // Check for duplicate email
        // Repository se kiem tra email da ton tai hay chua.
        if (accountService.isEmailExist(account.getEmail())) {
            // Gan loi vao field email de th:errors hien thi.
            bindingResult.rejectValue("email", "error.account", "Email đã được sử dụng");
        }

        // Check for duplicate phone number
        // Chi query trung so dien thoai khi field co gia tri.
        if (account.getPhoneNum() != null && !account.getPhoneNum().isEmpty()
                && accountService.isPhoneNumExist(account.getPhoneNum())) {
            // Gan loi vao field phoneNum neu so da thuoc account khac.
            bindingResult.rejectValue("phoneNum", "error.account", "Số điện thoại đã được sử dụng");
        }

        // Neu dob null gan -1; neu co thi tinh tuoi tron den ngay hien tai.
        int managerAge = account.getDob() == null
                ? -1
                : java.time.Period.between(account.getDob(), java.time.LocalDate.now()).getYears();
        // Manager phai du 18 tuoi.
        if (account.getDob() != null && managerAge < 18) {
            bindingResult.rejectValue("dob", "error.account", "Ng\u01b0\u1eddi d\u00f9ng ph\u1ea3i t\u1eeb 18 tu\u1ed5i tr\u1edf l\u00ean");
        // Gioi han tuoi toi da la 100.
        } else if (account.getDob() != null && managerAge > 100) {
            bindingResult.rejectValue("dob", "error.account", "Tu\u1ed5i kh\u00f4ng h\u1ee3p l\u1ec7 (kh\u00f4ng qu\u00e1 100 tu\u1ed5i)");
        }

        // Neu bat ky validation nao loi, render lai form cung Account da bind.
        if (bindingResult.hasErrors()) {
            return "create-manager-account";
        }

        // Save as MANAGER directly
        // Service gan role MANAGER, status active, diem mac dinh va save DB.
        accountService.createManagerAccount(account);

        redirectAttributes.addFlashAttribute("successMessage", "Tạo tài khoản quản lý thành công!");
        // Redirect de tranh tao trung khi refresh sau POST.
        return "redirect:/admin/dashboard";
    }
}
