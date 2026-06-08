package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TiÃªu Ä‘á» bÃ i viáº¿t
    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String title;

    // Danh má»¥c
    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String category;

    // TÃ¡c giáº£
    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String author;

    // Sapo / mÃ´ táº£ ngáº¯n
    @Column(nullable = false, columnDefinition = "NVARCHAR(1000)")
    private String summary;

    // Ná»™i dung bÃ i viáº¿t
    @Lob
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    // ÄÆ°á»ng dáº«n áº£nh Ä‘áº¡i diá»‡n
    @Column(columnDefinition = "NVARCHAR(500)")
    private String thumbnail;

    // Tags, phÃ¢n tÃ¡ch báº±ng dáº¥u pháº©y
    @Column(columnDefinition = "NVARCHAR(500)")
    private String tags;

    // DRAFT | PUBLISHED | SCHEDULED
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Post() {
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.publishedAt == null && "PUBLISHED".equalsIgnoreCase(this.status)) {
            this.publishedAt = this.createdAt;
        }

    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== Getter & Setter =====

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
