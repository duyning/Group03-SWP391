/**
 * Entity quản lý các Chương trình & Chiến dịch Khuyến mãi (`promotions`).
 * 
 * Khác với Voucher cá nhân (lưu mã giảm giá), Promotion là chiến dịch truyền thông hiển thị rộng rãi
 * trên trang chủ / trang khuyến mãi rạp (ví dụ: Happy Monday, Member Day, Ưu đãi Học sinh Sinh viên).
 * 
 * Hỗ trợ tự động tính toán tiến độ chiến dịch (`getProgressPercent`), số ngày còn lại (`getRemainingDays`),
 * trạng thái chiến dịch thực tế dựa trên thời gian thực (`getCampaignState`: Sắp diễn ra, Đang diễn ra, Đã kết thúc).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (25/06/2026)
 */
package com.group3.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(180)")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "NVARCHAR(40)")
    private CampaignType type = CampaignType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "NVARCHAR(40)")
    private TargetGroup targetGroup = TargetGroup.ALL;

    @Column(nullable = false, columnDefinition = "NVARCHAR(1000)")
    private String discountRule;

    @Column(nullable = false, columnDefinition = "NVARCHAR(1200)")
    private String description;

    @Column(nullable = false, columnDefinition = "NVARCHAR(1200)")
    private String conditionText;

    @Column(nullable = false, columnDefinition = "NVARCHAR(1000)")
    private String howToJoin;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String bannerImage;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private PromotionStatus status = PromotionStatus.DRAFT;

    @Column(nullable = false)
    private Integer displayOrder = 1;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        normalizeDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    private void normalizeDefaults() {
        if (displayOrder == null || displayOrder < 1) {
            displayOrder = 1;
        }
        if (status == null) {
            status = PromotionStatus.DRAFT;
        }
        if (type == null) {
            type = CampaignType.OTHER;
        }
        if (targetGroup == null) {
            targetGroup = TargetGroup.ALL;
        }
    }

    /**
     * Xác định trạng thái diễn ra thực tế của chiến dịch dựa trên thời gian hiện tại (`LocalDate.now()`).
     * 
     * @return CampaignState (DRAFT, INACTIVE, UPCOMING - Sắp diễn ra, RUNNING - Đang diễn ra, EXPIRED - Đã kết thúc).
     */
    public CampaignState getCampaignState() {
        if (status == PromotionStatus.DRAFT) {
            return CampaignState.DRAFT;
        }
        if (status == PromotionStatus.INACTIVE) {
            return CampaignState.INACTIVE;
        }
        LocalDate today = LocalDate.now();
        if (startDate != null && today.isBefore(startDate)) {
            return CampaignState.UPCOMING;
        }
        if (endDate != null && today.isAfter(endDate)) {
            return CampaignState.EXPIRED;
        }
        return CampaignState.RUNNING;
    }

    public String getCampaignStateDisplayName() {
        return getCampaignState().getDisplayName();
    }

    public String getCampaignStateCssClass() {
        return getCampaignState().getCssClass();
    }

    /**
     * Tính số ngày còn lại của chiến dịch khuyến mãi cho đến khi hết hạn.
     * 
     * @return Số ngày còn lại (dạng số nguyên long, tối thiểu là 0).
     */
    public long getRemainingDays() {
        if (endDate == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate));
    }

    /**
     * Tính tổng thời gian chiến dịch (số ngày từ ngày bắt đầu đến ngày kết thúc).
     */
    public long getTotalDays() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    /**
     * Tính số ngày đã trôi qua kể từ ngày bắt đầu chiến dịch.
     */
    public long getElapsedDays() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) {
            return 0;
        }
        if (today.isAfter(endDate)) {
            return getTotalDays();
        }
        return Math.max(1, ChronoUnit.DAYS.between(startDate, today) + 1);
    }

    /**
     * Tính tỷ lệ phần trăm tiến độ chiến dịch đã chạy (từ 0% đến 100%).
     */
    public int getProgressPercent() {
        long totalDays = getTotalDays();
        if (totalDays <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round((getElapsedDays() * 100.0) / totalDays));
    }

    /**
     * Kiểm tra xem chiến dịch khuyến mãi có đủ điều kiện hiển thị công khai trên giao diện người dùng không.
     * 
     * @return true nếu trạng thái ACTIVE và chưa trôi quá ngày kết thúc.
     */
    public boolean isPubliclyVisible() {
        return status == PromotionStatus.ACTIVE
                && endDate != null
                && !LocalDate.now().isAfter(endDate);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public CampaignType getType() {
        return type;
    }

    public void setType(CampaignType type) {
        this.type = type;
    }

    public TargetGroup getTargetGroup() {
        return targetGroup;
    }

    public void setTargetGroup(TargetGroup targetGroup) {
        this.targetGroup = targetGroup;
    }

    public String getDiscountRule() {
        return discountRule;
    }

    public void setDiscountRule(String discountRule) {
        this.discountRule = discountRule;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConditionText() {
        return conditionText;
    }

    public void setConditionText(String conditionText) {
        this.conditionText = conditionText;
    }

    public String getHowToJoin() {
        return howToJoin;
    }

    public void setHowToJoin(String howToJoin) {
        this.howToJoin = howToJoin;
    }

    public String getBannerImage() {
        return bannerImage;
    }

    public void setBannerImage(String bannerImage) {
        this.bannerImage = bannerImage;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public void setStatus(PromotionStatus status) {
        this.status = status;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum CampaignType {
        HAPPY_MONDAY("Happy Monday"),
        MEMBER_DAY("Member Day"),
        STUDENT_DISCOUNT("Ưu đãi học sinh / sinh viên"),
        BANK_PROMOTION("Ưu đãi ngân hàng"),
        SEASONAL("Chiến dịch theo mùa"),
        MOVIE_EVENT("Ưu đãi theo phim / sự kiện"),
        OTHER("Chiến dịch khác");

        private final String displayName;

        CampaignType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum TargetGroup {
        ALL("Tất cả khách hàng"),
        MEMBER("Thành viên"),
        STUDENT("Học sinh / sinh viên"),
        BANK_USER("Khách thanh toán ngân hàng"),
        NEW_CUSTOMER("Khách hàng mới"),
        FAMILY("Gia đình / nhóm bạn");

        private final String displayName;

        TargetGroup(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PromotionStatus {
        DRAFT("Nháp"),
        ACTIVE("Đang hiển thị"),
        INACTIVE("Tạm ẩn");

        private final String displayName;

        PromotionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum CampaignState {
        DRAFT("Nháp", "state-draft"),
        INACTIVE("Tạm ẩn", "state-inactive"),
        UPCOMING("Sắp diễn ra", "state-upcoming"),
        RUNNING("Đang diễn ra", "state-running"),
        EXPIRED("Đã kết thúc", "state-expired");

        private final String displayName;
        private final String cssClass;

        CampaignState(String displayName, String cssClass) {
            this.displayName = displayName;
            this.cssClass = cssClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssClass() {
            return cssClass;
        }
    }
}

