package com.group3.cinema.controller;

import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.FoodItem;
import com.group3.cinema.service.ComboService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller quản lý toàn bộ các chức năng liên quan đến Combo và Món ăn (Bắp/Nước).
 * Phân hệ dành riêng cho Quản trị viên (Admin Dashboard).
 *
 * @author Group 3 - Cinema Management System
 */
@Controller
@RequestMapping("/admin/combos")
public class ComboController {

    private final ComboService comboService;

    // Sử dụng Constructor Injection để đảm bảo tính Immutability và hỗ trợ Unit Testing
    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    /**
     * Hiển thị trang danh sách Combo và Danh mục món ăn lẻ.
     * Hỗ trợ tìm kiếm & lọc đa điều kiện theo từ khóa và trạng thái.
     *
     * @param keyword Từ khóa tìm kiếm combo theo tên
     * @param status Trạng thái lọc combo (ACTIVE, NEW, INACTIVE)
     * @param itemKeyword Từ khóa tìm kiếm món ăn lẻ
     * @param itemStatus Trạng thái lọc món ăn lẻ
     * @param model Đối tượng Model truyền dữ liệu sang Thymeleaf
     * @return Tên view 'combo-list'
     */
    @GetMapping
    public String listCombos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "itemKeyword", required = false) String itemKeyword,
            @RequestParam(value = "itemStatus", required = false) String itemStatus,
            Model model) {

        // Nạp dữ liệu danh sách Combo và Món ăn đã qua bộ lọc
        model.addAttribute("combos", comboService.searchCombos(keyword, status));
        model.addAttribute("foodItems", comboService.searchFoodItems(itemKeyword, itemStatus));
        model.addAttribute("foodCategories", comboService.getFoodCategories());

        // Chuẩn bị sẵn form object cho phần thêm nhanh món ăn ở chân trang
        if (!model.containsAttribute("foodItem")) {
            model.addAttribute("foodItem", new FoodItem());
        }
        return "combo-list";
    }

    /**
     * Hiển thị màn hình giao diện Tạo mới Combo.
     */
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("combo", new Combo());
        prepareComboForm(model, Map.of());
        return "combo-create";
    }

    /**
     * Xử lý lưu gói Combo mới vào CSDL.
     * Xử lý tải tệp ảnh, bắt lỗi Validation và tính toán lại tổng tiền chiết khấu.
     */
    @PostMapping("/save")
    public String saveCombo(
            @ModelAttribute("combo") Combo combo,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            @RequestParam(value = "foodItemIds", required = false) List<Long> foodItemIds,
            @RequestParam(value = "discountPercent", required = false) BigDecimal discountPercent,
            @RequestParam Map<String, String> requestParams,
            Model model) throws IOException {

        // Kiểm tra lỗi dữ liệu đầu vào theo Annotation Validation (@Valid)
        if (bindingResult.hasErrors()) {
            prepareComboForm(model, selectedQuantities(foodItemIds, requestParams));
            return "combo-create";
        }

        try {
            // Gọi Service thực thi logic nghiệp vụ: validate tên trùng, ghép món, tính giá vốn & giá bán
            comboService.createCombo(combo, file, foodItemIds, requestParams, discountPercent);
        } catch (IllegalArgumentException e) {
            // Bắt các ngoại lệ vi phạm quy tắc nghiệp vụ (Business Rules) và hiển thị lên UI
            bindingResult.reject("combo.business", e.getMessage());
            prepareComboForm(model, selectedQuantities(foodItemIds, requestParams));
            return "combo-create";
        }
        return "redirect:/admin/combos";
    }

    /**
     * Chuyển đổi nhanh trạng thái hoạt động của Combo (Mở bán ACTIVE <-> Ngừng bán INACTIVE).
     * Phục vụ tính năng Publish / Unpublish nhanh ngoài danh sách mà không cần mở trang edit.
     */
    @GetMapping("/publish/{id}")
    public String publishCombo(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            comboService.toggleComboStatus(id);
            redirectAttributes.addFlashAttribute("itemSuccess", "Đã cập nhật trạng thái hoạt động của Combo thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("itemError", "Không thể chuyển đổi trạng thái: " + e.getMessage());
        }
        return "redirect:/admin/combos";
    }

    /**
     * Xóa gói Combo khỏi hệ thống theo ID.
     */
    @GetMapping("/delete/{id}")
    public String deleteCombo(@PathVariable("id") Long id) {
        comboService.deleteCombo(id);
        return "redirect:/admin/combos";
    }

    /**
     * Hiển thị màn hình Chỉnh sửa thông tin Combo đã tồn tại.
     */
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Combo combo = comboService.getCombo(id);
        model.addAttribute("combo", combo);
        // Nạp danh sách các món ăn đã chọn và số lượng tương ứng của combo này
        prepareComboForm(model, comboService.getSelectedQuantities(combo));
        return "combo-edit";
    }

    /**
     * Xử lý cập nhật thông tin gói Combo.
     */
    @PostMapping("/update")
    public String updateCombo(
            @ModelAttribute("combo") Combo combo,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            @RequestParam(value = "foodItemIds", required = false) List<Long> foodItemIds,
            @RequestParam(value = "discountPercent", required = false) BigDecimal discountPercent,
            @RequestParam Map<String, String> requestParams,
            Model model) throws IOException {

        // Nếu form lỗi, giữ lại ảnh cũ để không vỡ giao diện hiển thị
        if (bindingResult.hasErrors()) {
            Combo oldCombo = comboService.getCombo(combo.getId());
            combo.setImage(oldCombo.getImage());
            prepareComboForm(model, selectedQuantities(foodItemIds, requestParams));
            return "combo-edit";
        }

        try {
            comboService.updateCombo(combo, file, foodItemIds, requestParams, discountPercent);
        } catch (IllegalArgumentException e) {
            Combo oldCombo = comboService.getCombo(combo.getId());
            combo.setImage(oldCombo.getImage());
            bindingResult.reject("combo.business", e.getMessage());
            prepareComboForm(model, selectedQuantities(foodItemIds, requestParams));
            return "combo-edit";
        }
        return "redirect:/admin/combos";
    }

    /**
     * Thêm mới món ăn/đồ uống lẻ vào danh mục gốc.
     */
    @PostMapping("/items/save")
    public String saveFoodItem(@ModelAttribute("foodItem") FoodItem foodItem,
                               RedirectAttributes redirectAttributes) {
        try {
            comboService.createFoodItem(foodItem);
            redirectAttributes.addFlashAttribute("itemSuccess", "Đã thêm món vào danh mục thành công.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("itemError", e.getMessage());
            redirectAttributes.addFlashAttribute("foodItem", foodItem);
        }
        return "redirect:/admin/combos";
    }

    /**
     * Cập nhật thông tin chi tiết (Tên, giá bán, giá vốn, trạng thái) của món ăn lẻ.
     */
    @PostMapping("/items/update")
    public String updateFoodItem(@ModelAttribute("foodItem") FoodItem foodItem,
                                 RedirectAttributes redirectAttributes) {
        try {
            comboService.updateFoodItem(foodItem);
            redirectAttributes.addFlashAttribute("itemSuccess", "Đã cập nhật thông tin món ăn.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("itemError", e.getMessage());
        }
        return "redirect:/admin/combos";
    }

    /**
     * Xóa món ăn khỏi CSDL hoặc tự động chuyển thành Ngừng bán (INACTIVE) nếu món đó đã nằm trong Combo.
     */
    @GetMapping("/items/delete/{id}")
    public String deleteFoodItem(@PathVariable("id") Long id,
                                 RedirectAttributes redirectAttributes) {
        comboService.deleteFoodItem(id);
        redirectAttributes.addFlashAttribute("itemSuccess", "Đã xóa hoặc chuyển trạng thái ngừng bán thành công.");
        return "redirect:/admin/combos";
    }

    /**
     * Hàm helper: Chuẩn bị dữ liệu danh mục món ăn đang kinh doanh và số lượng món chọn để gửi sang View.
     */
    private void prepareComboForm(Model model, Map<Long, Integer> selectedQuantities) {
        model.addAttribute("foodItems", comboService.getSellableFoodItems());
        model.addAttribute("foodCategories", comboService.getFoodCategories());
        model.addAttribute("selectedQuantities", selectedQuantities);
    }

    /**
     * Hàm helper: Trích xuất danh sách ID món ăn và số lượng tương ứng từ HTTP Request Parameters (`quantity_{id}`).
     */
    private Map<Long, Integer> selectedQuantities(List<Long> foodItemIds, Map<String, String> requestParams) {
        Map<Long, Integer> selectedQuantities = new LinkedHashMap<>();
        if (foodItemIds == null) {
            return selectedQuantities;
        }
        for (Long foodItemId : foodItemIds) {
            if (foodItemId == null) {
                continue;
            }
            try {
                // Đọc giá trị ô input số lượng dynamic dạng quantity_1, quantity_2,...
                selectedQuantities.put(foodItemId, Integer.parseInt(requestParams.get("quantity_" + foodItemId)));
            } catch (NumberFormatException e) {
                selectedQuantities.put(foodItemId, 1); // Mặc định là 1 nếu không thể parse số
            }
        }
        return selectedQuantities;
    }
}