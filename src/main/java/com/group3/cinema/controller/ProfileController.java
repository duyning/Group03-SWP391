package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller xá»­ lÃ½ hiá»ƒn thá»‹ há»“ sÆ¡ cÃ¡ nhÃ¢n (Profile).
 * Táº£i thÃ´ng tin má»›i nháº¥t cá»§a ngÆ°á»i dÃ¹ng Ä‘Ã£ Ä‘Äƒng nháº­p tá»« cÆ¡ sá»Ÿ dá»¯ liá»‡u Ä‘á»ƒ hiá»ƒn thá»‹.
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private AccountService accountService;

    @GetMapping
    public String viewProfile(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Reload fresh from DB
        Account account = accountService.findById(loggedInUser.getAccountID());
        if (account == null) {
            return "redirect:/login";
        }

        model.addAttribute("account", account);
        return "profile";
    }
}
