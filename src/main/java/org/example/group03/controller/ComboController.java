package org.example.group03.controller;

import lombok.RequiredArgsConstructor;
import org.example.group03.entity.Combo;
import org.example.group03.repository.ComboRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Controller
@RequestMapping("/admin/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ComboRepository comboRepository;

    // 1. Hiển thị danh sách kết hợp bộ lọc tìm kiếm
    @GetMapping
    public String listCombos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            Model model) {

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        List<Combo> filteredCombos = comboRepository.searchCombos(searchKeyword, status);

        model.addAttribute("combos", filteredCombos);

        // ĐÃ SỬA: Tìm trực tiếp file templates/combo-list.html
        return "combo-list";
    }

    // 2. Trả về giao diện thêm mới
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("combo", new Combo());

        // ĐÃ SỬA: Tìm trực tiếp file templates/combo-create.html
        return "combo-create";
    }

    // 3. Xử lý lưu combo khi thêm mới
    @PostMapping("/save")
    public String saveCombo(
            @ModelAttribute Combo combo,
            @RequestParam("imageFile") MultipartFile file) throws IOException {

        if (!file.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get("uploads");

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            combo.setImage("/uploads/" + fileName);
        }

        comboRepository.save(combo);
        return "redirect:/admin/combos";
    }

    // 4. Xóa combo theo ID
    @GetMapping("/delete/{id}")
    public String deleteCombo(@PathVariable Long id) {
        comboRepository.deleteById(id);
        return "redirect:/admin/combos";
    }

    // 5. Trả về giao diện chỉnh sửa
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói combo này"));
        model.addAttribute("combo", combo);

        // ĐÃ SỬA: Tìm trực tiếp file templates/combo-edit.html
        return "combo-edit";
    }

    // 6. Xử lý cập nhật dữ liệu Combo khi bấm nút Lưu thay đổi
    @PostMapping("/update")
    public String updateCombo(
            @ModelAttribute Combo combo,
            @RequestParam("imageFile") MultipartFile file) throws IOException {

        // Tìm combo gốc đang lưu trong Database để tránh mất dữ liệu ảnh
        Combo existingCombo = comboRepository.findById(combo.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói combo này"));

        // Gán các trường thông tin thay đổi từ form chỉnh sửa vào thực thể gốc
        existingCombo.setName(combo.getName());
        existingCombo.setPrice(combo.getPrice());
        existingCombo.setDescription(combo.getDescription());
        existingCombo.setStatus(combo.getStatus());

        // Xử lý logic file hình ảnh (Chỉ đổi nếu Admin up file mới)
        if (!file.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get("uploads");

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            existingCombo.setImage("/uploads/" + fileName);
        }

        // Tiến hành lưu đối tượng cập nhật xuống Database
        comboRepository.save(existingCombo);

        return "redirect:/admin/combos";
    }
}