/**
 * Entity lưu danh mục Sản phẩm đồ ăn bắp nước bán lẻ (`products`).
 * 
 * Chức năng:
 * - Lưu tên sản phẩm (`name`), giá bán lẻ (`price`), mô tả ngắn (`description`), trạng thái kinh doanh (`status`: ACTIVE, INACTIVE).
 * - Tự động thiết lập thời gian khởi tạo và cập nhật (`@PrePersist`, `@PreUpdate`).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Tên món không được để trống")
    @Size(max = 150, message = "Tên món không được vượt quá 150 ký tự")
    @Column(name = "product_name", nullable = false, length = 150, columnDefinition = "NVARCHAR(150)")
    private String name;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá bán không được nhỏ hơn 0")
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    @Column(name = "description", length = 255, columnDefinition = "NVARCHAR(255)")
    private String description;

    @NotBlank(message = "Vui lòng chọn trạng thái kinh doanh")
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private String status = "ACTIVE";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Tự động khởi tạo thời gian tạo, cập nhật và cắt khoảng trắng tên sản phẩm trước khi lưu (`@PrePersist`).
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "ACTIVE";
        if (this.name != null) this.name = this.name.trim();
    }

    /**
     * Tự động cập nhật thời gian sửa đổi và cắt khoảng trắng tên sản phẩm trước khi update (`@PreUpdate`).
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.name != null) this.name = this.name.trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}