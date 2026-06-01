package example.controller;

import example.entity.Account;
import example.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
            bindingResult.rejectValue("email", "error.account", "Email đã được sử dụng");
        }

        // Check for duplicate phone number
        if (account.getPhoneNum() != null && !account.getPhoneNum().isEmpty()
                && accountService.isPhoneNumExist(account.getPhoneNum())) {
            bindingResult.rejectValue("phoneNum", "error.account", "Số điện thoại đã được sử dụng");
        }

        // If validation errors exist, return back to the form
        if (bindingResult.hasErrors()) {
            return "register";
        }

        // Register the account
        accountService.register(account);

        redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
        return "redirect:/register?success";
    }
}
