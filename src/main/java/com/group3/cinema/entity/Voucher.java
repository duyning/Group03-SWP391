/**
 * Entity đại diện cho Mã giảm giá / Voucher (`vouchers`).
 * 
 * Chức năng:
 * - Quản lý thông tin mã voucher (`code`), tiêu đề chương trình (`title`), mức giảm (`discountValue`), giảm tối đa (`maxDiscountAmount`).
 * - Loại giảm giá (`DiscountType`: PERCENTAGE hoặc FIXED).
 * - Phạm vi áp dụng (`ServiceScope`: ALL, TICKET, WATER).
 * - Ngày áp dụng (`ApplicableDay`: ALL, WEEKDAY, WEEKEND) và cờ áp dụng ngày lễ Tết (`isHolidayApplicable`).
 * - Quản lý số lượng phát hành (`totalQuantity`), đã dùng (`usedQuantity`), giới hạn dùng mỗi tài khoản (`limitPerUser`).
 * - Hỗ trợ xóa mềm (`isDeleted`).
 * - Liên kết nhiều-nhiều với `Account` qua bảng trung gian `account_vouchers`.
 */
package com.group3.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "vouchers")
public class Voucher {

    public Voucher() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getMinOrderValue() {
        return minOrderValue;
    }

    public void setMinOrderValue(BigDecimal minOrderValue) {
        this.minOrderValue = minOrderValue;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getUsedQuantity() {
        return usedQuantity;
    }

    public void setUsedQuantity(Integer usedQuantity) {
        this.usedQuantity = usedQuantity;
    }

    public ServiceScope getServiceScope() {
        return serviceScope;
    }

    public void setServiceScope(ServiceScope serviceScope) {
        this.serviceScope = serviceScope;
    }

    public String getApplicableSeats() {
        return applicableSeats;
    }

    public void setApplicableSeats(String applicableSeats) {
        this.applicableSeats = applicableSeats;
    }

    public ApplicableDay getApplicableDays() {
        return applicableDays;
    }

    public void setApplicableDays(ApplicableDay applicableDays) {
        this.applicableDays = applicableDays;
    }

    public Boolean getIsHolidayApplicable() {
        return isHolidayApplicable;
    }

    public void setIsHolidayApplicable(Boolean isHolidayApplicable) {
        this.isHolidayApplicable = isHolidayApplicable;
    }

    public Integer getLimitPerUser() {
        return limitPerUser;
    }

    public void setLimitPerUser(Integer limitPerUser) {
        this.limitPerUser = limitPerUser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(Set<Account> accounts) {
        this.accounts = accounts;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Mã Voucher không được để trống")
    @Size(min = 3, max = 50, message = "Mã Voucher phải từ 3 đến 50 ký tự")
    @Column(name = "voucher_code", unique = true, nullable = false, length = 50)
    private String code;

    @NotBlank(message = "Tên chương trình không được để trống")
    @Size(max = 255, message = "Tên chương trình không được quá 255 ký tự")
    @Column(name = "title", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String title;

    @NotNull(message = "Trạng thái ẩn/hiện không được để trống")
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @NotNull(message = "Vui lòng chọn loại giảm giá")
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @NotNull(message = "Mức giảm giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Mức giảm giá không được nhỏ hơn 0")
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @DecimalMin(value = "0.0", inclusive = true, message = "Mức giảm tối đa không được nhỏ hơn 0")
    @Column(name = "max_discount_amount", precision = 15, scale = 2)
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @NotNull(message = "Ngày hết hạn không được để trống")
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @NotNull(message = "Giá trị hóa đơn tối thiểu không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá trị hóa đơn tối thiểu không được nhỏ hơn 0")
    @Column(name = "min_order_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal minOrderValue;

    @NotNull(message = "Tổng số lượng phát hành không được để trống")
    @Min(value = 1, message = "Tổng số lượng phát hành phải từ 1 trở lên")
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "used_quantity", nullable = false)
    @Builder.Default
    private Integer usedQuantity = 0;

    @NotNull(message = "Vui lòng chọn dịch vụ được áp dụng")
    @Enumerated(EnumType.STRING)
    @Column(name = "service_scope", nullable = false, length = 20)
    private ServiceScope serviceScope;

    @Column(name = "applicable_seats", length = 100)
    private String applicableSeats;

    @NotNull(message = "Vui lòng chọn ngày áp dụng trong tuần")
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_days", nullable = false, length = 20)
    private ApplicableDay applicableDays;

    @NotNull(message = "Vui lòng chọn cấu hình áp dụng ngày Lễ, Tết")
    @Column(name = "is_holiday_applicable", nullable = false)
    @Builder.Default
    private Boolean isHolidayApplicable = true;

    @NotNull(message = "Vui lòng cấu hình giới hạn lượt dùng")
    @Min(value = 1, message = "Giới hạn sử dụng mỗi tài khoản phải tối thiểu là 1")
    @Column(name = "limit_per_user", nullable = false)
    @Builder.Default
    private Integer limitPerUser = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(mappedBy = "savedVouchers")
    @JsonIgnore
    @Builder.Default
    private Set<Account> accounts = new HashSet<>();

    /**
     * Tự động khởi tạo thời gian tạo, cập nhật và viết hoa mã voucher trước khi insert vào DB (`@PrePersist`).
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.usedQuantity == null)
            this.usedQuantity = 0;
        if (this.isHolidayApplicable == null)
            this.isHolidayApplicable = true;
        if (this.limitPerUser == null)
            this.limitPerUser = 1;
        if (this.code != null)
            this.code = this.code.trim().toUpperCase();
    }

    /**
     * Tự động cập nhật thời gian sửa và viết hoa mã voucher trước khi update vào DB (`@PreUpdate`).
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.code != null)
            this.code = this.code.trim().toUpperCase();
    }

    /**
     * Enum loại giảm giá: PERCENTAGE (Phần trăm %), FIXED (Số tiền cố định).
     */
    public enum DiscountType {
        PERCENTAGE("Phần trăm (%)"),
        FIXED("Số tiền cố định (đ)");

        private final String displayName;

        DiscountType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Enum phạm vi dịch vụ áp dụng: ALL (Tất cả), TICKET (Vé phim), WATER (Bắp nước).
     */
    public enum ServiceScope {
        ALL("Tất cả dịch vụ"),
        TICKET("Chỉ áp dụng vé xem phim"),
        WATER("Chỉ áp dụng bắp nước (Combo)");

        private final String displayName;

        ServiceScope(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Enum ngày áp dụng: ALL (Tất cả ngày), WEEKDAY (Thứ 2 - Thứ 6), WEEKEND (Thứ 7 - CN).
     */
    public enum ApplicableDay {
        ALL("Tất cả các ngày trong tuần"),
        WEEKDAY("Ngày thường (Thứ 2 - Thứ 6)"),
        WEEKEND("Cuối tuần (Thứ 7 - Chủ Nhật)");

        private final String displayName;

        ApplicableDay(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}