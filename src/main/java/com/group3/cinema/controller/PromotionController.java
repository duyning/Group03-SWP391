package com.group3.cinema.controller;

/*
 * Created on 2026-06-25: Admin controller for promotion campaign management.
 * Updated: Added automated notification triggers.
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.entity.Promotion;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.NotificationService;
import com.group3.cinema.service.PromotionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final NotificationService notificationService; // Thêm
    private final AccountService accountService;             // Thêm

    public PromotionController(PromotionService promotionService,
                               NotificationService notificationService,
                               AccountService accountService) {
        this.promotionService = promotionService;
        this.notificationService = notificationService;
        this.accountService = accountService;
    }

    // --- CÁC METHOS GET, EDIT, HIDE... GIỮ NGUYÊN NHƯ CŨ ---
    @GetMapping
    public String listPromotions(@RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "type", required = false) Promotion.CampaignType type,
                                 @RequestParam(value = "targetGroup", required = false) Promotion.TargetGroup targetGroup,
                                 @RequestParam(value = "status", required = false) Promotion.PromotionStatus status,
                                 Model model) {
        List<Promotion> promotions = promotionService.searchPromotions(keyword, type, targetGroup, status);
        model.addAttribute("promotions", promotions);
        model.addAttribute("totalCampaigns", promotions.size());
        model.addAttribute("runningCampaigns", countByState(promotions, Promotion.CampaignState.RUNNING));
        model.addAttribute("upcomingCampaigns", countByState(promotions, Promotion.CampaignState.UPCOMING));
        model.addAttribute("inactiveCampaigns", promotions.stream()
                .filter(promotion -> promotion.getCampaignState() == Promotion.CampaignState.INACTIVE
                        || promotion.getCampaignState() == Promotion.CampaignState.EXPIRED
                        || promotion.getCampaignState() == Promotion.CampaignState.DRAFT)
                .count());
        model.addAttribute("types", Promotion.CampaignType.values());
        model.addAttribute("targetGroups", Promotion.TargetGroup.values());
        model.addAttribute("statuses", Promotion.PromotionStatus.values());
        return "promotion-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("promotion", new Promotion());
        addFormOptions(model);
        return "promotion-form";
    }

    @PostMapping("/save")
    public String savePromotion(@ModelAttribute("promotion") Promotion promotion,
                                @RequestParam(value = "bannerFile", required = false) MultipartFile bannerFile,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            promotionService.createPromotion(promotion, bannerFile);
            // Gửi thông báo sau khi lưu thành công
            sendNotificationToAll("Khuyến mãi mới: " + promotion.getTitle(), promotion.getDescription());
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo chiến dịch khuyến mãi.");
            return "redirect:/admin/promotions";
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("promotion", promotion);
            addFormOptions(model);
            return "promotion-form";
        }
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("promotion", promotionService.getPromotion(id));
        addFormOptions(model);
        return "promotion-form";
    }

    @PostMapping("/update/{id}")
    public String updatePromotion(@PathVariable("id") Long id,
                                  @ModelAttribute("promotion") Promotion promotion,
                                  @RequestParam(value = "bannerFile", required = false) MultipartFile bannerFile,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            promotionService.updatePromotion(id, promotion, bannerFile);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật chiến dịch khuyến mãi.");
            return "redirect:/admin/promotions";
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("errorMessage", e.getMessage());
            promotion.setId(id);
            model.addAttribute("promotion", promotion);
            addFormOptions(model);
            return "promotion-form";
        }
    }

    @GetMapping("/hide/{id}")
    public String hidePromotion(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        promotionService.hidePromotion(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạm ẩn chiến dịch khuyến mãi.");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/delete/{id}")
    public String deletePromotion(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            promotionService.deletePromotion(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa chiến dịch khuyến mãi.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/promotions";
    }

    @GetMapping("/activate/{id}")
    public String activatePromotion(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            promotionService.activatePromotion(id);
            // Gửi thông báo sau khi kích hoạt thành công
            Promotion p = promotionService.getPromotion(id);
            sendNotificationToAll("Ưu đãi đặc biệt: " + p.getTitle(), p.getDescription());
            redirectAttributes.addFlashAttribute("successMessage", "Đã kích hoạt chiến dịch khuyến mãi.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/promotions";
    }

    // --- HÀM HỖ TRỢ GỬI THÔNG BÁO (THÊM MỚI) ---
    private void sendNotificationToAll(String title, String content) {
        List<Account> accounts = accountService.findAll();
        for (Account acc : accounts) {
            notificationService.sendNotification(acc.getAccountID(), title, content, NotificationType.PROMOTION);
        }
    }

    private void addFormOptions(Model model) {
        model.addAttribute("types", Promotion.CampaignType.values());
        model.addAttribute("targetGroups", Promotion.TargetGroup.values());
        model.addAttribute("statuses", Promotion.PromotionStatus.values());
    }

    private long countByState(List<Promotion> promotions, Promotion.CampaignState state) {
        return promotions.stream()
                .filter(promotion -> promotion.getCampaignState() == state)
                .count();
    }
}