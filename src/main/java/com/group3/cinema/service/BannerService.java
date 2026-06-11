package com.group3.cinema.service;

/*
 * Created on 2026-06-09: Service for managing homepage and news banners.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Banner;
import com.group3.cinema.repository.BannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class BannerService {

    private static final Path UPLOAD_PATH = Paths.get("uploads", "banners");

    private final BannerRepository bannerRepository;

    public BannerService(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    public List<Banner> getBanners(Banner.BannerPage page) {
        if (page == null) {
            return bannerRepository.findAll();
        }
        return bannerRepository.findByPageOrderByDisplayOrderAscIdDesc(page);
    }

    public List<Banner> getActiveBanners(Banner.BannerPage page) {
        return bannerRepository.findByPageAndActiveTrueOrderByDisplayOrderAscIdDesc(page);
    }

    public Banner getActiveNewsBanner() {
        return bannerRepository.findFirstByPageAndActiveTrueOrderByDisplayOrderAscIdDesc(Banner.BannerPage.NEWS)
                .orElse(null);
    }

    public Banner getBanner(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy banner"));
    }

    @Transactional
    public void saveBanner(Banner banner, MultipartFile imageFile) throws IOException {
        if (banner.getTitle() != null) {
            banner.setTitle(banner.getTitle().trim());
        }
        if (banner.getSubtitle() != null) {
            banner.setSubtitle(emptyToNull(banner.getSubtitle().trim()));
        }
        if (banner.getLinkUrl() != null) {
            banner.setLinkUrl(emptyToNull(banner.getLinkUrl().trim()));
        }

        updateImageIfPresent(banner, imageFile);
        if (banner.getImageUrl() == null || banner.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh banner hoặc nhập URL ảnh.");
        }
        bannerRepository.save(banner);
    }

    @Transactional
    public void deleteBanner(Long id) {
        bannerRepository.deleteById(id);
    }

    private void updateImageIfPresent(Banner banner, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !isImage(originalName)) {
            throw new IllegalArgumentException("Ảnh banner chỉ hỗ trợ JPG, PNG hoặc WebP.");
        }

        Files.createDirectories(UPLOAD_PATH);
        String suffix = originalName.substring(originalName.lastIndexOf('.'));
        String fileName = UUID.randomUUID() + suffix;
        Files.copy(file.getInputStream(), UPLOAD_PATH.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        banner.setImageUrl("/uploads/banners/" + fileName);
    }

    private boolean isImage(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
