/**
 * Entity quản lý Bài viết / Tin tức điện ảnh (`posts`).
 * 
 * Chức năng:
 * - Quản lý tiêu đề bài viết (`title`), danh mục (`category`), tác giả (`author`), tóm tắt sapo (`summary`),
 *   nội dung chi tiết (`content`), đường dẫn ảnh đại diện (`thumbnail`), từ khóa tìm kiếm (`tags`).
 * - Trạng thái bài viết (`status`): DRAFT (Bài nháp), PUBLISHED (Xuất bản), SCHEDULED (Lên lịch xuất bản).
 * - Tự động cập nhật `publishedAt` khi chuyển trạng thái sang PUBLISHED và xóa `publishedAt` khi hạ bài xuống DRAFT.
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tiêu đề bài viết */
    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String title;

    /** Danh mục bài viết */
    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String category;

    /** Tác giả bài viết */
    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String author;

    /** Sapo / Mô tả ngắn bài viết */
    @Column(nullable = false, columnDefinition = "NVARCHAR(1000)")
    private String summary;

    /** Nội dung chi tiết định dạng HTML/Text */
    @Lob
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    /** Đường dẫn ảnh đại diện (thumbnail) */
    @Column(columnDefinition = "NVARCHAR(500)")
    private String thumbnail;

    /** Các từ khóa thẻ tag, phân tách bằng dấu phẩy */
    @Column(columnDefinition = "NVARCHAR(500)")
    private String tags;

    /** Trạng thái bài viết: DRAFT | PUBLISHED | SCHEDULED */
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Post() {
    }

    /**
     * Khởi tạo ngày tạo/sửa và tự động gắn ngày xuất bản nếu bài đăng ngay ở trạng thái PUBLISHED (`@PrePersist`).
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.publishedAt == null && "PUBLISHED".equalsIgnoreCase(this.status)) {
            this.publishedAt = this.createdAt;
        }

    }

    /**
     * Tự động cập nhật ngày sửa và đồng bộ ngày xuất bản khi thay đổi trạng thái bài viết (`@PreUpdate`).
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();

        if ("PUBLISHED".equalsIgnoreCase(this.status)) {
            if (this.publishedAt == null) {
                this.publishedAt = LocalDateTime.now();
            }
        } else if ("DRAFT".equalsIgnoreCase(this.status)) {
            this.publishedAt = null; // Hạ bài viết xuống thì xóa ngày xuất bản để ẩn khỏi trang tin tức
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

