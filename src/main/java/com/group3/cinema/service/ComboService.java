package com.group3.cinema.service;

import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.ComboDetail;
import com.group3.cinema.entity.Product;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.ProductRepository; // Nhớ tạo hoặc import Repository này vào
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComboService {

    private static final Path UPLOAD_PATH = Paths.get("uploads");

    private final ComboRepository comboRepository;
    private final ProductRepository productRepository; // Tiêm thêm để tìm món từ Menu

    public ComboService(ComboRepository comboRepository, ProductRepository productRepository) {
        this.comboRepository = comboRepository;
        this.productRepository = productRepository;
    }

    // ==========================================
    // 2 HÀM KIỂM TRA TRÙNG TÊN MỚI THÊM VÀO
    // ==========================================
    public boolean existsByName(String name) {
        return comboRepository.existsByName(name);
    }

    public boolean existsByNameAndIdNot(String name, Long id) {
        return comboRepository.existsByNameAndIdNot(name, id);
    }

    // ==========================================
    // CÁC HÀM CŨ ĐÃ ĐƯỢC NÂNG CẤP XỬ LÝ CHỌN MÓN ĐỘNG
    // ==========================================

    public List<Combo> searchCombos(String keyword, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return comboRepository.searchCombos(searchKeyword, status);
    }

    public Combo getCombo(Long id) {
        return comboRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay goi combo nay"));
    }

    /**
     * NÂNG CẤP: Thêm mới Combo đi kèm lưu danh sách thành phần món lẻ
     */
    @Transactional
    public void createCombo(Combo combo, List<Long> productIds, List<Integer> quantities, MultipartFile file) throws IOException {
        updateImageIfPresent(combo, file);

        // Tạo danh sách thành phần chi tiết từ mảng ID gửi lên
        if (productIds != null && !productIds.isEmpty()) {
            List<ComboDetail> details = new ArrayList<>();
            for (int i = 0; i < productIds.size(); i++) {
                Long productId = productIds.get(i);
                Integer quantity = quantities.get(i);

                if (productId != null) {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn trong Menu với ID: " + productId));

                    ComboDetail detail = new ComboDetail();
                    detail.setCombo(combo);
                    detail.setProduct(product);
                    detail.setQuantity(quantity);
                    details.add(detail);
                }
            }
            combo.setComboDetails(details);
        }

        comboRepository.save(combo);
    }

    /**
     * NÂNG CẤP: Cập nhật Combo và làm sạch (Overwrite) danh sách món cũ thành món mới
     */
    @Transactional
    public void updateCombo(Combo combo, List<Long> productIds, List<Integer> quantities, MultipartFile file) throws IOException {
        Combo existingCombo = getCombo(combo.getId());
        existingCombo.setName(combo.getName());
        existingCombo.setPrice(combo.getPrice());

        // Vứt dòng này đi vì Entity Combo không còn trường description nữa cậu nhé!
        // existingCombo.setDescription(combo.getDescription());

        existingCombo.setStatus(combo.getStatus());
        updateImageIfPresent(existingCombo, file);

        // Làm sạch danh sách chi tiết cũ (JPA orphanRemoval = true sẽ tự động delete dưới DB)
        existingCombo.getComboDetails().clear();

        // Cập nhật tập hợp thành phần món mới
        if (productIds != null && !productIds.isEmpty()) {
            for (int i = 0; i < productIds.size(); i++) {
                Long productId = productIds.get(i);
                Integer quantity = quantities.get(i);

                if (productId != null) {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn trong Menu với ID: " + productId));

                    ComboDetail detail = new ComboDetail();
                    detail.setCombo(existingCombo);
                    detail.setProduct(product);
                    detail.setQuantity(quantity);

                    existingCombo.getComboDetails().add(detail);
                }
            }
        }

        comboRepository.save(existingCombo);
    }

    @Transactional
    public void deleteCombo(Long id) {
        comboRepository.deleteById(id);
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