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

@Controller
@RequestMapping("/admin/combos")
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    public String listCombos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "itemKeyword", required = false) String itemKeyword,
            @RequestParam(value = "itemStatus", required = false) String itemStatus,
            Model model) {
        model.addAttribute("combos", comboService.searchCombos(keyword, status));
        model.addAttribute("foodItems", comboService.searchFoodItems(itemKeyword, itemStatus));
        model.addAttribute("foodCategories", comboService.getFoodCategories());
        if (!model.containsAttribute("foodItem")) {
            model.addAttribute("foodItem", new FoodItem());
        }
        return "combo-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("combo", new Combo());
        prepareComboForm(model, Map.of());
        return "combo-create";
    }

    @PostMapping("/save")
    public String saveCombo(
            @ModelAttribute("combo") Combo combo,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            @RequestParam(value = "foodItemIds", required = false) List<Long> foodItemIds,
            @RequestParam(value = "discountPercent", required = false) BigDecimal discountPercent,
            @RequestParam Map<String, String> requestParams,
            Model model) throws IOException {

        if (bindingResult.hasErrors()) {
            prepareComboForm(model, selectedQuantities(foodItemIds, requestParams));
            return "combo-create";
        }

        try {
            comboService.createCombo(combo, file, foodItemIds, requestParams, discountPercent);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("combo.business", e.getMessage());
            prepareComboForm(model, selectedQuantities(foodItemIds, requestParams));
            return "combo-create";
        }
        return "redirect:/admin/combos";
    }

    /* =========================================================================
       HÀM XỬ LÝ CHUYỂN ĐỔI TRẠNG THÁI ACTIVE / INACTIVE ĐƯỢC THÊM MỚI TẠI ĐÂY
       ========================================================================= */
    @GetMapping("/publish/{id}")
    public String publishCombo(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            comboService.toggleComboStatus(id); // Gọi hàm xử lý logic đổi trạng thái ở Service
            redirectAttributes.addFlashAttribute("itemSuccess", "Đã cập nhật trạng thái hoạt động của Combo thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("itemError", "Không thể chuyển đổi trạng thái: " + e.getMessage());
        }
        return "redirect:/admin/combos";
    }

    @GetMapping("/delete/{id}")
    public String deleteCombo(@PathVariable("id") Long id) {
        comboService.deleteCombo(id);
        return "redirect:/admin/combos";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Combo combo = comboService.getCombo(id);
        model.addAttribute("combo", combo);
        prepareComboForm(model, comboService.getSelectedQuantities(combo));
        return "combo-edit";
    }

    @PostMapping("/update")
    public String updateCombo(
            @ModelAttribute("combo") Combo combo,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile file,
            @RequestParam(value = "foodItemIds", required = false) List<Long> foodItemIds,
            @RequestParam(value = "discountPercent", required = false) BigDecimal discountPercent,
            @RequestParam Map<String, String> requestParams,
            Model model) throws IOException {

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

    @PostMapping("/items/save")
    public String saveFoodItem(@ModelAttribute("foodItem") FoodItem foodItem,
                               RedirectAttributes redirectAttributes) {
        try {
            comboService.createFoodItem(foodItem);
            redirectAttributes.addFlashAttribute("itemSuccess", "Đã thêm món vào danh mục.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("itemError", e.getMessage());
            redirectAttributes.addFlashAttribute("foodItem", foodItem);
        }
        return "redirect:/admin/combos";
    }

    @PostMapping("/items/update")
    public String updateFoodItem(@ModelAttribute("foodItem") FoodItem foodItem,
                                 RedirectAttributes redirectAttributes) {
        try {
            comboService.updateFoodItem(foodItem);
            redirectAttributes.addFlashAttribute("itemSuccess", "Đã cập nhật món ăn.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("itemError", e.getMessage());
        }
        return "redirect:/admin/combos";
    }

    @GetMapping("/items/delete/{id}")
    public String deleteFoodItem(@PathVariable("id") Long id,
                                 RedirectAttributes redirectAttributes) {
        comboService.deleteFoodItem(id);
        redirectAttributes.addFlashAttribute("itemSuccess", "Đã xóa hoặc ngừng bán món nếu món đang nằm trong combo.");
        return "redirect:/admin/combos";
    }

    private void prepareComboForm(Model model, Map<Long, Integer> selectedQuantities) {
        model.addAttribute("foodItems", comboService.getSellableFoodItems());
        model.addAttribute("foodCategories", comboService.getFoodCategories());
        model.addAttribute("selectedQuantities", selectedQuantities);
    }

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
                selectedQuantities.put(foodItemId, Integer.parseInt(requestParams.get("quantity_" + foodItemId)));
            } catch (NumberFormatException e) {
                selectedQuantities.put(foodItemId, 1);
            }
        }
        return selectedQuantities;
    }
}