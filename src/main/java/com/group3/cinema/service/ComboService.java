package com.group3.cinema.service;

import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.ComboItem;
import com.group3.cinema.entity.FoodItem;
import com.group3.cinema.repository.ComboItemRepository;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.FoodItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ComboService {

    private static final Path UPLOAD_PATH = Paths.get("uploads");
    private static final BigDecimal MAX_ITEM_PRICE = new BigDecimal("1000000");
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("80");
    private static final List<String> FOOD_CATEGORIES = List.of(
            "Bắp nước",
            "Đồ uống",
            "Đồ ăn nhanh",
            "Snack",
            "Khác");
    private static final Set<String> SELLABLE_ITEM_STATUSES = Set.of("ACTIVE", "NEW");
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "NEW", "INACTIVE");

    private final ComboRepository comboRepository;
    private final FoodItemRepository foodItemRepository;
    private final ComboItemRepository comboItemRepository;

    public ComboService(ComboRepository comboRepository,
                        FoodItemRepository foodItemRepository,
                        ComboItemRepository comboItemRepository) {
        this.comboRepository = comboRepository;
        this.foodItemRepository = foodItemRepository;
        this.comboItemRepository = comboItemRepository;
    }

    // ==========================================
    // 2 HÀM KIỂM TRA TRÙNG TÊN MỚI THÊM VÀO
    // ==========================================

    /**
     * Dùng khi Thêm mới: Check xem tên này đã có ai dùng chưa
     */
    public boolean existsByName(String name) {
        return comboRepository.existsByName(name);
    }

    /**
     * Dùng khi Cập nhật: Check xem tên này có bị trùng với các combo KHÁC không (trừ chính nó ra)
     */
    public boolean existsByNameAndIdNot(String name, Long id) {
        return comboRepository.existsByNameAndIdNot(name, id);
    }

    // ==========================================
    // CÁC HÀM CŨ GIỮ NGUYÊN LOGIC
    // ==========================================

    public List<Combo> searchCombos(String keyword, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return comboRepository.searchCombos(searchKeyword, status);
    }

    public List<FoodItem> searchFoodItems(String keyword, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return foodItemRepository.searchFoodItems(searchKeyword, status);
    }

    public List<FoodItem> getSellableFoodItems() {
        return foodItemRepository.findByStatusInOrderByNameAsc(SELLABLE_ITEM_STATUSES);
    }

    public List<String> getFoodCategories() {
        return FOOD_CATEGORIES;
    }

    public Combo getCombo(Long id) {
        return comboRepository.findWithItemsById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay goi combo nay"));
    }

    public FoodItem getFoodItem(Long id) {
        return foodItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay mon an nay"));
    }

    public Map<Long, Integer> getSelectedQuantities(Combo combo) {
        if (combo == null || combo.getItems() == null) {
            return Map.of();
        }
        return combo.getItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getFoodItem().getId(),
                        ComboItem::getQuantity,
                        Integer::sum,
                        LinkedHashMap::new));
    }

    @Transactional
    public void createCombo(Combo combo,
                            MultipartFile file,
                            List<Long> foodItemIds,
                            Map<String, String> requestParams,
                            BigDecimal discountPercent) throws IOException {
        validateComboName(combo.getName(), null);
        normalizeComboStatus(combo);
        applyCalculatedPricing(combo, foodItemIds, requestParams, discountPercent);
        updateImageIfPresent(combo, file);
        comboRepository.save(combo);
    }

    @Transactional
    public void updateCombo(Combo combo,
                            MultipartFile file,
                            List<Long> foodItemIds,
                            Map<String, String> requestParams,
                            BigDecimal discountPercent) throws IOException {
        Combo existingCombo = getCombo(combo.getId());
        validateComboName(combo.getName(), combo.getId());
        existingCombo.setName(combo.getName());
        existingCombo.setStatus(combo.getStatus());
        normalizeComboStatus(existingCombo);
        applyCalculatedPricing(existingCombo, foodItemIds, requestParams, discountPercent);
        updateImageIfPresent(existingCombo, file);
        comboRepository.save(existingCombo);
    }

    @Transactional
    public void deleteCombo(Long id) {
        comboRepository.deleteById(id);
    }

    /* =========================================================================
       HÀM ĐỔI TRẠNG THÁI ACTIVE / INACTIVE CHO NÚT PUBLISH ĐÃ ĐƯỢC CHÈN VÀO ĐÂY
       ========================================================================= */
    @Transactional
    public void toggleComboStatus(Long id) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói combo này với ID: " + id));

        if ("INACTIVE".equals(combo.getStatus())) {
            combo.setStatus("ACTIVE");
        } else {
            combo.setStatus("INACTIVE");
        }
        comboRepository.save(combo);
    }

    @Transactional
    public void createFoodItem(FoodItem foodItem) {
        validateFoodItem(foodItem, null);
        foodItemRepository.save(foodItem);
    }

    @Transactional
    public void updateFoodItem(FoodItem foodItem) {
        FoodItem existingItem = getFoodItem(foodItem.getId());
        validateFoodItem(foodItem, existingItem.getId());
        existingItem.setName(foodItem.getName());
        existingItem.setCategory(foodItem.getCategory());
        existingItem.setUnitPrice(foodItem.getUnitPrice());
        existingItem.setCostPrice(foodItem.getCostPrice());
        existingItem.setDescription(foodItem.getDescription());
        existingItem.setStatus(foodItem.getStatus());
        foodItemRepository.save(existingItem);
    }

    @Transactional
    public void deleteFoodItem(Long id) {
        FoodItem foodItem = getFoodItem(id);
        if (comboItemRepository.existsByFoodItemId(id)) {
            foodItem.setStatus("INACTIVE");
            foodItemRepository.save(foodItem);
            return;
        }
        foodItemRepository.delete(foodItem);
    }

    private void validateComboName(String name, Long currentId) {
        String normalizedName = normalizeText(name);
        if (normalizedName == null || normalizedName.length() < 3) {
            throw new IllegalArgumentException("Tên combo phải có ít nhất 3 ký tự.");
        }
        if (normalizedName.length() > 150) {
            throw new IllegalArgumentException("Tên combo không được vượt quá 150 ký tự.");
        }
        boolean duplicated = currentId == null
                ? comboRepository.existsByName(normalizedName)
                : comboRepository.existsByNameAndIdNot(normalizedName, currentId);
        if (duplicated) {
            throw new IllegalArgumentException("Tên combo này đã tồn tại trong hệ thống.");
        }
    }

    private void applyCalculatedPricing(Combo combo,
                                        List<Long> foodItemIds,
                                        Map<String, String> requestParams,
                                        BigDecimal discountPercent) {
        List<ComboItem> comboItems = buildComboItems(foodItemIds, requestParams);
        BigDecimal originalPrice = BigDecimal.ZERO;
        BigDecimal costPrice = BigDecimal.ZERO;

        for (ComboItem item : comboItems) {
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
            originalPrice = originalPrice.add(item.getUnitPrice().multiply(quantity));
            costPrice = costPrice.add(item.getCostPrice().multiply(quantity));
        }

        BigDecimal normalizedDiscountPercent = normalizeDiscountPercent(discountPercent);
        BigDecimal discountAmount = originalPrice
                .multiply(normalizedDiscountPercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal salePrice = originalPrice.subtract(discountAmount);

        if (salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá bán sau chiết khấu phải lớn hơn 0.");
        }
        if (salePrice.compareTo(costPrice) < 0) {
            throw new IllegalArgumentException("Giá bán sau chiết khấu không được thấp hơn giá vốn combo.");
        }

        combo.setOriginalPrice(originalPrice);
        combo.setCostPrice(costPrice);
        combo.setDiscountPercent(normalizedDiscountPercent);
        combo.setDiscountAmount(discountAmount);
        combo.setPrice(salePrice);
        combo.setDescription(buildComboDescription(comboItems));
        combo.setItems(comboItems);
    }

    private List<ComboItem> buildComboItems(List<Long> foodItemIds, Map<String, String> requestParams) {
        if (foodItemIds == null || foodItemIds.isEmpty()) {
            throw new IllegalArgumentException("Combo phải có ít nhất 1 món ăn hoặc đồ uống.");
        }

        Map<Long, Integer> selectedItems = new LinkedHashMap<>();
        for (Long foodItemId : foodItemIds) {
            if (foodItemId == null) {
                continue;
            }
            int quantity = parseQuantity(requestParams.get("quantity_" + foodItemId));
            selectedItems.merge(foodItemId, quantity, Integer::sum);
        }

        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("Combo phải có ít nhất 1 món ăn hoặc đồ uống.");
        }

        List<ComboItem> comboItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : selectedItems.entrySet()) {
            FoodItem foodItem = getFoodItem(entry.getKey());
            if (!SELLABLE_ITEM_STATUSES.contains(foodItem.getStatus())) {
                throw new IllegalArgumentException("Món " + foodItem.getName() + " đang ngừng bán nên không thể đưa vào combo.");
            }
            ComboItem comboItem = new ComboItem();
            comboItem.setFoodItem(foodItem);
            comboItem.setQuantity(entry.getValue());
            comboItem.setUnitPrice(foodItem.getUnitPrice());
            comboItem.setCostPrice(foodItem.getCostPrice());
            comboItems.add(comboItem);
        }
        return comboItems;
    }

    private int parseQuantity(String rawQuantity) {
        try {
            int quantity = Integer.parseInt(rawQuantity);
            if (quantity < 1 || quantity > 20) {
                throw new IllegalArgumentException("Số lượng mỗi món trong combo phải từ 1 đến 20.");
            }
            return quantity;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Số lượng món trong combo không hợp lệ.");
        }
    }

    private String buildComboDescription(List<ComboItem> comboItems) {
        return comboItems.stream()
                .map(item -> item.getQuantity() + " x " + item.getFoodItem().getName())
                .collect(Collectors.joining(" + "));
    }

    private BigDecimal normalizeDiscountPercent(BigDecimal discountPercent) {
        BigDecimal normalizedDiscount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        if (normalizedDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Chiết khấu combo không được nhỏ hơn 0%.");
        }
        if (normalizedDiscount.compareTo(MAX_DISCOUNT_PERCENT) > 0) {
            throw new IllegalArgumentException("Chiết khấu combo không được vượt quá 80%.");
        }
        return normalizedDiscount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateFoodItem(FoodItem foodItem, Long currentId) {
        foodItem.setName(normalizeText(foodItem.getName()));
        foodItem.setCategory(normalizeText(foodItem.getCategory()));
        foodItem.setDescription(normalizeText(foodItem.getDescription()));
        foodItem.setStatus(normalizeStatus(foodItem.getStatus()));

        if (foodItem.getName() == null || foodItem.getName().length() < 2) {
            throw new IllegalArgumentException("Tên món phải có ít nhất 2 ký tự.");
        }
        if (foodItem.getName().length() > 150) {
            throw new IllegalArgumentException("Tên món không được vượt quá 150 ký tự.");
        }
        if (foodItem.getCategory() == null || foodItem.getCategory().length() < 2) {
            throw new IllegalArgumentException("Vui lòng chọn danh mục món.");
        }
        if (!FOOD_CATEGORIES.contains(foodItem.getCategory())) {
            throw new IllegalArgumentException("Danh mục món không hợp lệ. Vui lòng chọn trong danh sách.");
        }
        if (foodItem.getDescription() != null && foodItem.getDescription().length() > 255) {
            throw new IllegalArgumentException("Mô tả món không được vượt quá 255 ký tự.");
        }
        validateMoney(foodItem.getUnitPrice(), "Giá bán lẻ");
        validateMoney(foodItem.getCostPrice(), "Giá vốn");
        if (foodItem.getCostPrice().compareTo(foodItem.getUnitPrice()) > 0) {
            throw new IllegalArgumentException("Giá vốn không được lớn hơn giá bán lẻ.");
        }

        boolean duplicated = currentId == null
                ? foodItemRepository.existsByNameIgnoreCase(foodItem.getName())
                : foodItemRepository.existsByNameIgnoreCaseAndIdNot(foodItem.getName(), currentId);
        if (duplicated) {
            throw new IllegalArgumentException("Tên món này đã tồn tại trong danh mục.");
        }
    }

    private void validateMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " là bắt buộc.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " không được âm.");
        }
        if (value.compareTo(MAX_ITEM_PRICE) > 0) {
            throw new IllegalArgumentException(fieldName + " không được vượt quá 1.000.000 VNĐ.");
        }
    }

    private void normalizeComboStatus(Combo combo) {
        combo.setName(normalizeText(combo.getName()));
        combo.setStatus(normalizeStatus(combo.getStatus()));
    }

    private String normalizeStatus(String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus == null || normalizedStatus.isBlank()) {
            return "ACTIVE";
        }
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }
        return normalizedStatus;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void updateImageIfPresent(Combo combo, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        if (!Files.exists(UPLOAD_PATH)) {
            Files.createDirectories(UPLOAD_PATH);
        }

        Files.copy(file.getInputStream(), UPLOAD_PATH.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        combo.setImage("/uploads/" + fileName);
    }
}