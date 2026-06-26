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
public class AdminManagerController {

    @Autowired
    private AccountService accountService;

    @GetMapping("/admin/create-manager")
    public String showCreateManagerForm(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ Admin mới có quyền truy cập chức năng này!");
            return "redirect:/admin/dashboard";
        }
        model.addAttribute("account", new Account());
        return "create-manager-account";
    }

    @PostMapping("/admin/create-manager")
    public String processCreateManager(
            @Valid @ModelAttribute("account") Account account,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ Admin mới có quyền thực hiện chức năng này!");
            return "redirect:/admin/dashboard";
        }

        // Check for duplicate email
        if (accountService.isEmailExist(account.getEmail())) {
            bindingResult.rejectValue("email", "error.account", "Email đã được sử dụng");
        }

        // Check for duplicate phone number
        if (account.getPhoneNum() != null && !account.getPhoneNum().isEmpty()
                && accountService.isPhoneNumExist(account.getPhoneNum())) {
            bindingResult.rejectValue("phoneNum", "error.account", "Số điện thoại đã được sử dụng");
        }

        int managerAge = account.getDob() == null
                ? -1
                : java.time.Period.between(account.getDob(), java.time.LocalDate.now()).getYears();
        if (account.getDob() != null && managerAge < 18) {
            bindingResult.rejectValue("dob", "error.account", "Ng\u01b0\u1eddi d\u00f9ng ph\u1ea3i t\u1eeb 18 tu\u1ed5i tr\u1edf l\u00ean");
        } else if (account.getDob() != null && managerAge > 100) {
            bindingResult.rejectValue("dob", "error.account", "Tu\u1ed5i kh\u00f4ng h\u1ee3p l\u1ec7 (kh\u00f4ng qu\u00e1 100 tu\u1ed5i)");
        }

        if (bindingResult.hasErrors()) {
            return "create-manager-account";
        }

        // Save as MANAGER directly
        accountService.createManagerAccount(account);

        redirectAttributes.addFlashAttribute("successMessage", "Tạo tài khoản quản lý thành công!");
        return "redirect:/admin/dashboard";
    }
}
