/**
 * Entity ghi lại nhật ký hoạt động hệ thống (`activity_logs`).
 * 
 * Áp dụng theo dõi thao tác người dùng ở mọi vai trò: ADMIN, MANAGER, CUSTOMER.
 * 
 * Đóng vai trò:
 * - Lưu ID tài khoản thực hiện (`accountId`), mã loại hành động (`ActionType`),
 *   mô tả chi tiết hành động (`description`), địa chỉ IP máy khách (`ipAddress`) và thời gian thực hiện (`createdAt`).
 * - Phục vụ giao diện truy vấn nhật ký hệ thống (`activity-log.html`) cho Admin.
 * 
 * Ngày thực hiện: 09/07/2026
 * Khởi tạo bởi: DuongND_HE186619
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs", indexes = {
    @Index(name = "idx_activity_log_account", columnList = "accountId"),
    @Index(name = "idx_activity_log_created_at", columnList = "createdAt")
})
public class ActivityLog {

    /**
     * Enum các loại hành động nhật ký hệ thống:
     * LOGIN, LOGOUT, PROFILE_UPDATE, PASSWORD_CHANGE, TICKET_BOOKING, PAYMENT, VOUCHER_USE, v.v.
     */
    public enum ActionType {
        LOGIN, LOGOUT,
        PROFILE_UPDATE, PASSWORD_CHANGE,
        TICKET_VIEW, TICKET_BOOKING, TICKET_CANCEL,
        PAYMENT,
        VOUCHER_SAVE, VOUCHER_USE,
        MOVIE_VIEW,
        POST_CREATE, POST_EDIT, POST_DELETE,
        ACCOUNT_CREATE, ACCOUNT_EDIT,
        SHOWTIME_CREATE, SHOWTIME_EDIT, SHOWTIME_DELETE,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActionType action;

    @Column(length = 500, columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column(length = 255)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ActivityLog() {
    }

    public ActivityLog(Integer accountId, ActionType action, String description) {
        this.accountId = accountId;
        this.action = action;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public ActivityLog(Integer accountId, ActionType action, String description, String ipAddress) {
        this(accountId, action, description);
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }

    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }

    public ActionType getAction() { return action; }
    public void setAction(ActionType action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Trả về tên hiển thị tiếng Việt của hành động phục vụ Thymeleaf UI.
     * 
     * @return Tên hành động tiếng Việt tương ứng.
     */
    public String getActionDisplayName() {
        return switch (action) {
            case LOGIN -> "Đăng nhập";
            case LOGOUT -> "Đăng xuất";
            case PROFILE_UPDATE -> "Cập nhật hồ sơ";
            case PASSWORD_CHANGE -> "Đổi mật khẩu";
            case TICKET_VIEW -> "Xem vé";
            case TICKET_BOOKING -> "Đặt vé";
            case TICKET_CANCEL -> "Hủy vé";
            case PAYMENT -> "Thanh toán";
            case VOUCHER_SAVE -> "Lưu voucher";
            case VOUCHER_USE -> "Sử dụng voucher";
            case MOVIE_VIEW -> "Xem phim";
            case POST_CREATE -> "Đăng bài viết";
            case POST_EDIT -> "Sửa bài viết";
            case POST_DELETE -> "Xóa bài viết";
            case ACCOUNT_CREATE -> "Tạo tài khoản";
            case ACCOUNT_EDIT -> "Chỉnh sửa tài khoản";
            case SHOWTIME_CREATE -> "Tạo suất chiếu";
            case SHOWTIME_EDIT -> "Sửa suất chiếu";
            case SHOWTIME_DELETE -> "Xóa suất chiếu";
            default -> "Hoạt động khác";
        };
    }

    /**
     * Trả về CSS FontAwesome class icon biểu thị hành động cho giao diện nhật ký.
     * 
     * @return Tên class icon CSS.
     */
    public String getActionIconClass() {
        return switch (action) {
            case LOGIN -> "fa-sign-in-alt log-icon--login";
            case LOGOUT -> "fa-sign-out-alt log-icon--logout";
            case PROFILE_UPDATE -> "fa-user-edit log-icon--profile";
            case PASSWORD_CHANGE -> "fa-key log-icon--password";
            case TICKET_BOOKING, TICKET_VIEW, TICKET_CANCEL -> "fa-ticket-alt log-icon--ticket";
            case PAYMENT -> "fa-credit-card log-icon--payment";
            case VOUCHER_SAVE, VOUCHER_USE -> "fa-tags log-icon--voucher";
            case MOVIE_VIEW -> "fa-film log-icon--movie";
            case POST_CREATE, POST_EDIT, POST_DELETE -> "fa-newspaper log-icon--post";
            case ACCOUNT_CREATE, ACCOUNT_EDIT -> "fa-users-cog log-icon--account";
            case SHOWTIME_CREATE, SHOWTIME_EDIT, SHOWTIME_DELETE -> "fa-calendar-alt log-icon--showtime";
            default -> "fa-circle-dot log-icon--other";
        };
    }
}

