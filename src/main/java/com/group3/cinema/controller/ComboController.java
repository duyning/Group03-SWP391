package com.group3.cinema.controller;

import com.group3.cinema.entity.Combo;
import com.group3.cinema.service.ComboService;
import com.group3.cinema.repository.ProductRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/combos")
public class ComboController {

    private final ComboService comboService;
    private final ProductRepository productRepository;

    public ComboController(ComboService comboService, ProductRepository productRepository) {
        this.comboService = comboService;
        this.productRepository = productRepository;
    }

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
        model.addAttribute("products", productRepository.findByStatus("ACTIVE"));
        return "combo-create";
    }

    @PostMapping("/save")
    public String saveCombo(
            @ModelAttribute("combo") Combo combo,
            BindingResult bindingResult,
            @RequestParam(value = "productIds[]", required = false) List<Long> productIds,
            @RequestParam(value = "quantities[]", required = false) List<Integer> quantities,
            @RequestParam("imageFile") MultipartFile file,
            Model model) throws IOException {

        // 1. Kiểm tra logic nghiệp vụ (Tên đã tồn tại)
        if (comboService.existsByName(combo.getName())) {
            bindingResult.rejectValue("name", "error.combo", "Tên gói combo này đã tồn tại trong hệ thống!");
        }

        // 2. Nếu có lỗi (validation hoặc logic), trả về form kèm danh sách sản phẩm
        if (bindingResult.hasErrors()) {
            // CỰC KỲ QUAN TRỌNG: Phải nạp lại danh sách sản phẩm để bảng chọn món không bị trống
            model.addAttribute("products", productRepository.findByStatus("ACTIVE"));
            return "combo-create";
        }

        // 3. Nếu mọi thứ OK, gọi service lưu dữ liệu
        comboService.createCombo(combo, productIds, quantities, file);
        return "redirect:/admin/combos";
    }

    // TRẢ LẠI HÀM GETMAPPING CHUẨN ĐỂ KHỚP URL NÚT XÓA TRÊN GIAO DIỆN HTML
    @GetMapping("/delete/{id}")
    public String deleteCombo(@PathVariable("id") Long id) {
        // Gọi sang Service xử lý xóa mềm, không ôm repo xử lý trực tiếp ở đây
        comboService.deleteCombo(id);
        return "redirect:/admin/combos";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("combo", comboService.getCombo(id));
        model.addAttribute("products", productRepository.findByStatus("ACTIVE"));
        return "combo-edit";
    }

    @PostMapping("/update")
    public String updateCombo(
            @ModelAttribute("combo") Combo combo,
            BindingResult bindingResult,
            @RequestParam(value = "productIds[]", required = false) List<Long> productIds,
            @RequestParam(value = "quantities[]", required = false) List<Integer> quantities,
            @RequestParam("imageFile") MultipartFile file,
            Model model) throws IOException {

        if (comboService.existsByNameAndIdNot(combo.getName(), combo.getId())) {
            bindingResult.rejectValue("name", "error.combo", "Tên gói combo này đã bị trùng với một combo khác!");
        }

        if (bindingResult.hasErrors()) {
            Combo oldCombo = comboService.getCombo(combo.getId());
            combo.setImage(oldCombo.getImage());
            model.addAttribute("products", productRepository.findByStatus("ACTIVE"));
            return "combo-edit";
        }

        comboService.updateCombo(combo, productIds, quantities, file);
        return "redirect:/admin/combos";
    }
}