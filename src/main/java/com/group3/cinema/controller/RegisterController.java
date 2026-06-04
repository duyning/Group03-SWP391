package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.AccountService;
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
 * Controller xá»­ lÃ½ tÃ­nh nÄƒng Ä‘Äƒng kÃ½ tÃ i khoáº£n (Register).
 * Kiá»ƒm tra tÃ­nh há»£p lá»‡ dá»¯ liá»‡u Ä‘áº§u vÃ o vÃ  kiá»ƒm tra trÃ¹ng láº·p email/sá»‘ Ä‘iá»‡n thoáº¡i trÆ°á»›c khi Ä‘Äƒng kÃ½.
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
@Controller
public class RegisterController {

    @Autowired
    private AccountService accountService;

    /**
     * Show the registration form.
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
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
            RedirectAttributes redirectAttributes) {

        // Check for duplicate email
        if (accountService.isEmailExist(account.getEmail())) {
            bindingResult.rejectValue("email", "error.account", "Email Ä‘Ã£ Ä‘Æ°á»£c sá»­ dá»¥ng");
        }

        // Check for duplicate phone number
        if (account.getPhoneNum() != null && !account.getPhoneNum().isEmpty()
                && accountService.isPhoneNumExist(account.getPhoneNum())) {
            bindingResult.rejectValue("phoneNum", "error.account", "Sá»‘ Ä‘iá»‡n thoáº¡i Ä‘Ã£ Ä‘Æ°á»£c sá»­ dá»¥ng");
        }

        // If validation errors exist, return back to the form
        if (bindingResult.hasErrors()) {
            return "register";
        }

        // Register the account
        accountService.register(account);

        redirectAttributes.addFlashAttribute("successMessage", "ÄÄƒng kÃ½ thÃ nh cÃ´ng! Vui lÃ²ng Ä‘Äƒng nháº­p.");
        return "redirect:/register?success";
    }
}
