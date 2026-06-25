package com.group3.cinema.controller;

/*
 * Created on 2026-06-25: Customer-facing promotion campaign pages.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Promotion;
import com.group3.cinema.service.PromotionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CustomerPromotionController {

    private final PromotionService promotionService;

    public CustomerPromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping({"/promotions", "/uu-dai"})
    public String showPromotions(@RequestParam(value = "filter", required = false, defaultValue = "all") String filter,
                                 HttpSession session,
                                 Model model) {
        addLoggedInUser(session, model);
        model.addAttribute("promotions", promotionService.getPublicPromotions(filter));
        model.addAttribute("activeFilter", filter);
        return "customer-promotion-list";
    }

    @GetMapping({"/promotions/{id}", "/uu-dai/{id}"})
    public String showPromotionDetail(@PathVariable("id") Long id,
                                      HttpSession session,
                                      Model model) {
        Promotion promotion = promotionService.getPublicPromotion(id);
        addLoggedInUser(session, model);
        model.addAttribute("promotion", promotion);
        model.addAttribute("relatedPromotions", promotionService.getPublicPromotions("all").stream()
                .filter(item -> !item.getId().equals(id))
                .limit(3)
                .toList());
        return "customer-promotion-detail";
    }

    private void addLoggedInUser(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
    }
}
