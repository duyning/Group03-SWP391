package com.group3.cinema.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Liên kết ngược để có thể truy vấn: Món này đang nằm trong những combo nào?
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ComboDetail> comboDetails = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "ACTIVE";
        if (this.name != null) this.name = this.name.trim();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.name != null) this.name = this.name.trim();
    }

    // ===== GETTERS AND SETTERS =====
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

    public List<ComboDetail> getComboDetails() { return comboDetails; }
    public void setComboDetails(List<ComboDetail> comboDetails) { this.comboDetails = comboDetails; }
}