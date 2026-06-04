package com.group3.cinema.controller;

import com.group3.cinema.entity.Combo;
import com.group3.cinema.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RequestMapping("/admin/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    @GetMapping
    public String listCombos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            Model model) {
        model.addAttribute("combos", comboService.searchCombos(keyword, status));
        return "combo-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("combo", new Combo());
        return "combo-create";
    }

    @PostMapping("/save")
    public String saveCombo(
            @ModelAttribute Combo combo,
            @RequestParam("imageFile") MultipartFile file) throws IOException {
        comboService.createCombo(combo, file);
        return "redirect:/admin/combos";
    }

    @GetMapping("/delete/{id}")
    public String deleteCombo(@PathVariable Long id) {
        comboService.deleteCombo(id);
        return "redirect:/admin/combos";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("combo", comboService.getCombo(id));
        return "combo-edit";
    }

    @PostMapping("/update")
    public String updateCombo(
            @ModelAttribute Combo combo,
            @RequestParam("imageFile") MultipartFile file) throws IOException {
        comboService.updateCombo(combo, file);
        return "redirect:/admin/combos";
    }
}
