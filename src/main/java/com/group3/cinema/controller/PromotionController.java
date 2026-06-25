package com.group3.cinema.controller;

/*
 * Created on 2026-06-25: Admin controller for promotion campaign management.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Promotion;
import com.group3.cinema.service.PromotionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

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
            redirectAttributes.addFlashAttribute("successMessage", "Đã kích hoạt chiến dịch khuyến mãi.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/promotions";
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
