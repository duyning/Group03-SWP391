/**
 * Entity đại diện cho Quảng cáo Banner (`banners`) hiển thị trên giao diện người dùng.
 * 
 * Chức năng:
 * - Quản lý tiêu đề banner (`title`), mô tả phụ (`subtitle`), đường dẫn hình ảnh (`imageUrl`), đường dẫn liên kết (`linkUrl`).
 * - Phân loại trang hiển thị (`BannerPage`: HOME - Trang chủ, NEWS - Trang tin tức).
 * - Quản lý thứ tự ưu tiên hiển thị (`displayOrder`) và cờ trạng thái (`active`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (09/06/2026)
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

import java.time.LocalDateTime;

@Entity
@Table(name = "banners")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(150)")
    private String title;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String subtitle;

    @Column(nullable = false, columnDefinition = "NVARCHAR(500)")
    private String imageUrl;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private BannerPage page;

    @Column(nullable = false)
    private Integer displayOrder = 1;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.displayOrder == null || this.displayOrder < 1) {
            this.displayOrder = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.displayOrder == null || this.displayOrder < 1) {
            this.displayOrder = 1;
        }
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

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public BannerPage getPage() {
        return page;
    }

    public void setPage(BannerPage page) {
        this.page = page;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Enum định nghĩa trang vị trí hiển thị banner.
     */
    public enum BannerPage {
        HOME("Trang chủ"),
        NEWS("Trang tin tức");

        private final String displayName;

        BannerPage(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}

