package com.group3.cinema.controller.api;

/*
 * Created on 2026-06-25: REST API for promotion campaign data.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Promotion;
import com.group3.cinema.service.PromotionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promotions")
public class PromotionApiController {

    private final PromotionService promotionService;

    public PromotionApiController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public List<Promotion> listPromotions(@RequestParam(value = "keyword", required = false) String keyword,
                                          @RequestParam(value = "type", required = false) Promotion.CampaignType type,
                                          @RequestParam(value = "targetGroup", required = false) Promotion.TargetGroup targetGroup,
                                          @RequestParam(value = "status", required = false) Promotion.PromotionStatus status) {
        return promotionService.searchPromotions(keyword, type, targetGroup, status);
    }

    @GetMapping("/active")
    public List<Promotion> activePromotions(@RequestParam(value = "filter", required = false, defaultValue = "all") String filter) {
        return promotionService.getPublicPromotions(filter);
    }

    @GetMapping("/{id}")
    public Promotion getPromotion(@PathVariable("id") Long id) {
        return promotionService.getPromotion(id);
    }

    @PostMapping
    public ResponseEntity<?> createPromotion(@RequestBody Promotion promotion) throws IOException {
        try {
            return ResponseEntity.ok(promotionService.createPromotion(promotion, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePromotion(@PathVariable("id") Long id,
                                             @RequestBody Promotion promotion) throws IOException {
        try {
            return ResponseEntity.ok(promotionService.updatePromotion(id, promotion, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activatePromotion(@PathVariable("id") Long id) {
        try {
            promotionService.activatePromotion(id);
            return ResponseEntity.ok(Map.of("message", "Đã kích hoạt chiến dịch."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/hide")
    public ResponseEntity<Map<String, String>> hidePromotion(@PathVariable("id") Long id) {
        promotionService.hidePromotion(id);
        return ResponseEntity.ok(Map.of("message", "Đã tạm ẩn chiến dịch."));
    }
}
