package com.group3.cinema.service;

/*
 * Created on 2026-06-09: Service for managing homepage and news banners.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Banner;
import com.group3.cinema.repository.BannerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BannerService {

    private static final Path UPLOAD_PATH = Paths.get("uploads", "banners");
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;

    private final BannerRepository bannerRepository;

    public BannerService(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    public List<Banner> getBanners(Banner.BannerPage page) {
        if (page == null) {
            return bannerRepository.findAll(Sort.by(
                    Sort.Order.asc("page"),
                    Sort.Order.desc("id")
            ));
        }
        return bannerRepository.findByPageOrderByIdDesc(page);
    }

    public List<Banner> getActiveBanners(Banner.BannerPage page) {
        return bannerRepository.findByPageAndActiveTrueOrderByIdDesc(page);
    }

    public Banner getActiveNewsBanner() {
        return bannerRepository.findFirstByPageAndActiveTrueOrderByIdDesc(Banner.BannerPage.NEWS)
                .orElse(null);
    }

    public Banner getBanner(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy banner"));
    }

    @Transactional
    public void saveBanner(Banner banner, MultipartFile imageFile) throws IOException {
        normalizeBanner(banner);
        updateImageIfPresent(banner, imageFile);
        validateBanner(banner);
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
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Dung lượng ảnh banner không được vượt quá 5MB.");
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
        String lower = stripQuery(fileName).toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private void normalizeBanner(Banner banner) {
        banner.setTitle(normalizeText(banner.getTitle()));
        banner.setSubtitle(emptyToNull(trim(banner.getSubtitle())));
        banner.setImageUrl(emptyToNull(trim(banner.getImageUrl())));
        banner.setLinkUrl(emptyToNull(trim(banner.getLinkUrl())));
        if (banner.getDisplayOrder() == null || banner.getDisplayOrder() < 1) {
            banner.setDisplayOrder(1);
        }
    }

    private void validateBanner(Banner banner) {
        if (banner.getTitle() == null || banner.getTitle().length() < 5 || banner.getTitle().length() > 150) {
            throw new IllegalArgumentException("Tên banner nội bộ phải từ 5 đến 150 ký tự.");
        }
        if (banner.getPage() == null) {
            throw new IllegalArgumentException("Vui lòng chọn vị trí hiển thị banner.");
        }
        if (bannerRepository.existsDuplicateTitleInPage(banner.getTitle(), banner.getPage(), banner.getId())) {
            throw new IllegalArgumentException("Tên banner đã tồn tại ở vị trí này. Vui lòng dùng tên khác.");
        }
        if (banner.getSubtitle() != null && banner.getSubtitle().length() > 500) {
            throw new IllegalArgumentException("Ghi chú nội bộ không được vượt quá 500 ký tự.");
        }
        if (banner.getImageUrl() == null || banner.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh banner hoặc nhập URL ảnh.");
        }
        validateImageReference(banner.getImageUrl());
        if (banner.getLinkUrl() != null && !banner.getLinkUrl().matches("^/movies/\\d+$")) {
            throw new IllegalArgumentException("Liên kết banner chỉ được trỏ tới phim đang có trong hệ thống.");
        }
    }

    private void validateImageReference(String value) {
        if (value.length() > 500) {
            throw new IllegalArgumentException("URL ảnh banner không được vượt quá 500 ký tự.");
        }
        if (!(value.startsWith("/") || value.startsWith("http://") || value.startsWith("https://"))) {
            throw new IllegalArgumentException("URL ảnh banner phải là URL http(s) hoặc đường dẫn nội bộ bắt đầu bằng /.");
        }
        if (!isImage(value)) {
            throw new IllegalArgumentException("Ảnh banner phải là JPG, PNG hoặc WebP.");
        }
        if (value.contains("<") || value.contains(">") || value.contains("\"")) {
            throw new IllegalArgumentException("URL ảnh banner chứa ký tự không hợp lệ.");
        }
    }

    private String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        int hashIndex = withoutQuery.indexOf('#');
        return hashIndex >= 0 ? withoutQuery.substring(0, hashIndex) : withoutQuery;
    }

    private String normalizeText(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", " ");
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
