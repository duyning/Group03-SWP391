package com.group3.cinema.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "combos")
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Tên combo không được để trống")
    @Size(max = 150, message = "Tên combo không được vượt quá 150 ký tự")
    @Column(name = "combo_name", nullable = false, length = 150, columnDefinition = "NVARCHAR(150)") // Đảm bảo đúng tên 'combo_name'
    private String name;

    @NotNull(message = "Giá bán combo không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá combo không được nhỏ hơn 0")
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 255, columnDefinition = "NVARCHAR(255)")
    private String image;

    @NotBlank(message = "Vui lòng chọn trạng thái mở bán")
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Liên kết Một-Nhiều xuốn bảng chi tiết (Thay thế hoàn toàn cho description dạng chữ cũ)
    // Sửa FetchType.LAZY thành EAGER
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
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

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<ComboDetail> getComboDetails() { return comboDetails; }
    public void setComboDetails(List<ComboDetail> comboDetails) { this.comboDetails = comboDetails; }
}