package com.group3.cinema.service;

import com.group3.cinema.entity.Combo;
import com.group3.cinema.repository.ComboRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class ComboService {

    private static final Path UPLOAD_PATH = Paths.get("uploads");

    private final ComboRepository comboRepository;

    public ComboService(ComboRepository comboRepository) {
        this.comboRepository = comboRepository;
    }

    public List<Combo> searchCombos(String keyword, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return comboRepository.searchCombos(searchKeyword, status);
    }

    public Combo getCombo(Long id) {
        return comboRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay goi combo nay"));
    }

    @Transactional
    public void createCombo(Combo combo, MultipartFile file) throws IOException {
        updateImageIfPresent(combo, file);
        comboRepository.save(combo);
    }

    @Transactional
    public void updateCombo(Combo combo, MultipartFile file) throws IOException {
        Combo existingCombo = getCombo(combo.getId());
        existingCombo.setName(combo.getName());
        existingCombo.setPrice(combo.getPrice());
        existingCombo.setDescription(combo.getDescription());
        existingCombo.setStatus(combo.getStatus());
        updateImageIfPresent(existingCombo, file);
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
