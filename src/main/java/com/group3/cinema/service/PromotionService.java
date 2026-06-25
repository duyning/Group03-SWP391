package com.group3.cinema.service;

/*
 * Created on 2026-06-25: Service layer for cinema promotion campaigns.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Promotion;
import com.group3.cinema.repository.PromotionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PromotionService {

    private static final Path UPLOAD_PATH = Paths.get("uploads", "promotions");
    private static final long MAX_BANNER_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_CAMPAIGN_DAYS = 365;

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    public List<Promotion> searchPromotions(String keyword,
                                            Promotion.CampaignType type,
                                            Promotion.TargetGroup targetGroup,
                                            Promotion.PromotionStatus status) {
        String searchKeyword = keyword != null && !keyword.trim().isEmpty() ? keyword.trim() : null;
        return promotionRepository.searchPromotions(searchKeyword, type, targetGroup, status);
    }

    public List<Promotion> getPublicPromotions(String filter) {
        List<Promotion> promotions = promotionRepository
                .findByStatusAndEndDateGreaterThanEqualOrderByStartDateAscIdDesc(
                        Promotion.PromotionStatus.ACTIVE,
                        LocalDate.now()
                );
        String normalizedFilter = filter == null ? "all" : filter.toLowerCase(Locale.ROOT);
        return promotions.stream()
                .filter(promotion -> matchesPublicFilter(promotion, normalizedFilter))
                .toList();
    }

    public Promotion getPromotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chiến dịch khuyến mãi."));
    }

    public Promotion getPublicPromotion(Long id) {
        return promotionRepository
                .findByIdAndStatusAndEndDateGreaterThanEqual(id, Promotion.PromotionStatus.ACTIVE, LocalDate.now())
                .orElseThrow(() -> new IllegalArgumentException("Chiến dịch không tồn tại hoặc đã ngừng hiển thị."));
    }

    @Transactional
    public Promotion createPromotion(Promotion promotion, MultipartFile bannerFile) throws IOException {
        normalize(promotion);
        updateBannerIfPresent(promotion, bannerFile);
        validatePromotion(promotion, null);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public Promotion updatePromotion(Long id, Promotion form, MultipartFile bannerFile) throws IOException {
        Promotion existing = getPromotion(id);
        existing.setTitle(form.getTitle());
        existing.setType(form.getType());
        existing.setTargetGroup(form.getTargetGroup());
        existing.setDiscountRule(form.getDiscountRule());
        existing.setDescription(form.getDescription());
        existing.setConditionText(form.getConditionText());
        existing.setHowToJoin(form.getHowToJoin());
        existing.setStartDate(form.getStartDate());
        existing.setEndDate(form.getEndDate());
        existing.setStatus(form.getStatus());

        if (form.getBannerImage() != null && !form.getBannerImage().isBlank()) {
            existing.setBannerImage(form.getBannerImage().trim());
        }

        normalize(existing);
        updateBannerIfPresent(existing, bannerFile);
        validatePromotion(existing, existing.getId());
        return promotionRepository.save(existing);
    }

    @Transactional
    public void hidePromotion(Long id) {
        Promotion promotion = getPromotion(id);
        promotion.setStatus(Promotion.PromotionStatus.INACTIVE);
        promotionRepository.save(promotion);
    }

    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = getPromotion(id);
        promotionRepository.delete(promotion);
    }

    @Transactional
    public void activatePromotion(Long id) {
        Promotion promotion = getPromotion(id);
        promotion.setStatus(Promotion.PromotionStatus.ACTIVE);
        normalize(promotion);
        validatePromotion(promotion, promotion.getId());
        promotionRepository.save(promotion);
    }

    private boolean matchesPublicFilter(Promotion promotion, String filter) {
        return switch (filter) {
            case "running" -> promotion.getCampaignState() == Promotion.CampaignState.RUNNING;
            case "upcoming" -> promotion.getCampaignState() == Promotion.CampaignState.UPCOMING;
            case "member" -> promotion.getTargetGroup() == Promotion.TargetGroup.MEMBER;
            case "student" -> promotion.getTargetGroup() == Promotion.TargetGroup.STUDENT;
            case "bank" -> promotion.getTargetGroup() == Promotion.TargetGroup.BANK_USER
                    || promotion.getType() == Promotion.CampaignType.BANK_PROMOTION;
            default -> true;
        };
    }

    private void normalize(Promotion promotion) {
        promotion.setTitle(normalizeText(promotion.getTitle()));
        promotion.setDescription(trim(promotion.getDescription()));
        promotion.setDiscountRule(trim(promotion.getDiscountRule()));
        promotion.setConditionText(trim(promotion.getConditionText()));
        promotion.setHowToJoin(trim(promotion.getHowToJoin()));
        promotion.setBannerImage(trimToNull(promotion.getBannerImage()));
        if (promotion.getDisplayOrder() == null || promotion.getDisplayOrder() < 1) {
            promotion.setDisplayOrder(1);
        }
        if (promotion.getStatus() == null) {
            promotion.setStatus(Promotion.PromotionStatus.DRAFT);
        }
        if (promotion.getType() == null) {
            promotion.setType(Promotion.CampaignType.OTHER);
        }
        if (promotion.getTargetGroup() == null) {
            promotion.setTargetGroup(Promotion.TargetGroup.ALL);
        }
    }

    private void validatePromotion(Promotion promotion, Long currentId) {
        if (isBlank(promotion.getTitle()) || promotion.getTitle().length() < 5 || promotion.getTitle().length() > 180) {
            throw new IllegalArgumentException("Tên chiến dịch phải từ 5 đến 180 ký tự.");
        }
        if (promotionRepository.existsDuplicateTitle(promotion.getTitle(), currentId)) {
            throw new IllegalArgumentException("Tên chiến dịch đã tồn tại. Vui lòng đặt tên khác để dễ quản lý.");
        }
        if (promotion.getType() == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại chiến dịch.");
        }
        if (promotion.getTargetGroup() == null) {
            throw new IllegalArgumentException("Vui lòng chọn nhóm khách hàng áp dụng.");
        }
        if (promotion.getStatus() == null) {
            throw new IllegalArgumentException("Vui lòng chọn trạng thái quản trị.");
        }
        if (promotion.getStartDate() == null || promotion.getEndDate() == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày bắt đầu và ngày kết thúc chiến dịch.");
        }
        if (promotion.getEndDate().isBefore(promotion.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        }
        long campaignDays = ChronoUnit.DAYS.between(promotion.getStartDate(), promotion.getEndDate()) + 1;
        if (campaignDays > MAX_CAMPAIGN_DAYS) {
            throw new IllegalArgumentException("Thời gian chạy chiến dịch không nên vượt quá 365 ngày.");
        }
        if (isBlank(promotion.getDiscountRule()) || promotion.getDiscountRule().length() < 10 || promotion.getDiscountRule().length() > 1000) {
            throw new IllegalArgumentException("Luật ưu đãi phải từ 10 đến 1000 ký tự.");
        }
        if (isBlank(promotion.getDescription()) || promotion.getDescription().length() < 30) {
            throw new IllegalArgumentException("Mô tả chiến dịch phải rõ ràng, tối thiểu 30 ký tự.");
        }
        if (promotion.getDescription().length() > 1200) {
            throw new IllegalArgumentException("Mô tả chiến dịch không được vượt quá 1200 ký tự.");
        }
        if (isBlank(promotion.getConditionText()) || promotion.getConditionText().length() < 10 || promotion.getConditionText().length() > 1200) {
            throw new IllegalArgumentException("Điều kiện tham gia phải từ 10 đến 1200 ký tự.");
        }
        if (isBlank(promotion.getHowToJoin()) || promotion.getHowToJoin().length() < 10 || promotion.getHowToJoin().length() > 1000) {
            throw new IllegalArgumentException("Cách khách hàng nhận ưu đãi phải từ 10 đến 1000 ký tự.");
        }
        if (!isBlank(promotion.getBannerImage())) {
            validateImageReference(promotion.getBannerImage(), "URL banner chiến dịch");
        }
        if (promotion.getStatus() == Promotion.PromotionStatus.ACTIVE) {
            validateActiveCampaign(promotion, currentId);
        }
    }

    private void validateActiveCampaign(Promotion promotion, Long currentId) {
        if (isBlank(promotion.getBannerImage())) {
            throw new IllegalArgumentException("Chiến dịch đang hiển thị phải có banner.");
        }
        if (promotion.getEndDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Không thể kích hoạt chiến dịch đã hết hạn.");
        }
        long overlaps = promotionRepository.countOverlappingActiveCampaigns(
                promotion.getType(),
                promotion.getTargetGroup(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                Promotion.PromotionStatus.ACTIVE,
                currentId
        );
        if (overlaps > 0) {
            throw new IllegalArgumentException("Đã có chiến dịch cùng loại và cùng nhóm khách hàng trong khoảng thời gian này.");
        }
    }

    private void updateBannerIfPresent(Promotion promotion, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        if (file.getSize() > MAX_BANNER_SIZE_BYTES) {
            throw new IllegalArgumentException("Dung lượng banner không được vượt quá 5MB.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !isImage(originalName)) {
            throw new IllegalArgumentException("Banner chỉ hỗ trợ JPG, PNG hoặc WebP.");
        }
        Files.createDirectories(UPLOAD_PATH);
        String suffix = originalName.substring(originalName.lastIndexOf('.'));
        String fileName = UUID.randomUUID() + suffix;
        Files.copy(file.getInputStream(), UPLOAD_PATH.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        promotion.setBannerImage("/uploads/promotions/" + fileName);
    }

    private boolean isImage(String fileName) {
        String lower = stripQuery(fileName).toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private void validateImageReference(String value, String fieldName) {
        if (value.length() > 500) {
            throw new IllegalArgumentException(fieldName + " không được vượt quá 500 ký tự.");
        }
        if (!(value.startsWith("/") || value.startsWith("http://") || value.startsWith("https://"))) {
            throw new IllegalArgumentException(fieldName + " phải là URL http(s) hoặc đường dẫn nội bộ bắt đầu bằng /.");
        }
        if (!isImage(value)) {
            throw new IllegalArgumentException(fieldName + " phải trỏ tới ảnh JPG, PNG hoặc WebP.");
        }
        if (value.contains("<") || value.contains(">") || value.contains("\"")) {
            throw new IllegalArgumentException(fieldName + " chứa ký tự không hợp lệ.");
        }
    }

    private String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        int hashIndex = withoutQuery.indexOf('#');
        return hashIndex >= 0 ? withoutQuery.substring(0, hashIndex) : withoutQuery;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeText(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }
}
