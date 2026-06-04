/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.controller;

import com.group3.cinema.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/catalogs")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public String catalogPage(Model model) {
        model.addAttribute("roomTypes", catalogService.getAllRoomTypes());
        model.addAttribute("audioTechnologies", catalogService.getAllAudioTechnologies());
        model.addAttribute("seatTypes", catalogService.getAllSeatTypes());
        return "manager_catalog";
    }

    @PostMapping("/room-types/add")
    public String addRoomType(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.addRoomType(name, description);
            redirectAttributes.addFlashAttribute("successMessage", "ÄÃ£ thÃªm loáº¡i phÃ²ng \"" + name + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/catalogs";
    }

    @PostMapping("/room-types/edit")
    public String editRoomType(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.updateRoomType(id, name, description, active);
            redirectAttributes.addFlashAttribute("successMessage", "ÄÃ£ cáº­p nháº­t loáº¡i phÃ²ng \"" + name + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/catalogs";
    }

    @PostMapping("/audio/add")
    public String addAudioTechnology(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.addAudioTechnology(name, description);
            redirectAttributes.addFlashAttribute("successMessage", "ÄÃ£ thÃªm Ã¢m thanh \"" + name + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/catalogs";
    }

    @PostMapping("/audio/edit")
    public String editAudioTechnology(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.updateAudioTechnology(id, name, description, active);
            redirectAttributes.addFlashAttribute("successMessage", "ÄÃ£ cáº­p nháº­t Ã¢m thanh \"" + name + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/catalogs";
    }

    @PostMapping("/seat-types/edit")
    public String editSeatType(
            @RequestParam Long id,
            @RequestParam String color,
            @RequestParam int capacity,
            @RequestParam(defaultValue = "false") boolean sellable,
            @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.updateSeatType(id, color, capacity, sellable, active);
            redirectAttributes.addFlashAttribute("successMessage", "ÄÃ£ cáº­p nháº­t cáº¥u hÃ¬nh loáº¡i gháº¿.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/catalogs";
    }

    @PostMapping("/seat-types/add")
    public String addSeatType(
            @RequestParam String displayName,
            @RequestParam String color,
            @RequestParam int capacity,
            @RequestParam(defaultValue = "false") boolean sellable,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.addSeatType(displayName, color, capacity, sellable);
            redirectAttributes.addFlashAttribute("successMessage", "ÄÃ£ thÃªm loáº¡i gháº¿ \"" + displayName + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/catalogs";
    }
}
