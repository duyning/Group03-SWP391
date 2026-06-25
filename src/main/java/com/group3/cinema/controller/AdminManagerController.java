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

        if (account.getDob() != null && !account.isValidAge()) {
            bindingResult.rejectValue("dob", "error.account", "Tuổi không hợp lệ (phải từ 13 đến 100 tuổi).");
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
