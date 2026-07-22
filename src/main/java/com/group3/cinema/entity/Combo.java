/**
 * Entity quản lý Gói Combo Bắp Nước (`combos`) phục vụ bán vé online và bán vé tại quầy.
 * 
 * Chức năng:
 * - Lưu thông tin tên gói combo (`name`), mô tả (`description`), hình ảnh (`image`).
 * - Lưu chi tiết cấu trúc giá: Giá niêm yết bán (`price`), Giá gốc chưa giảm (`originalPrice`),
 *   Tỷ lệ giảm (% `discountPercent`), Giá vốn (`costPrice`).
 * - Liên kết danh sách các món đồ ăn/uống chi tiết cấu thành combo (`items`: `List<ComboItem>`).
 * - Quản lý trạng thái bán ("ACTIVE" - Đang bán, "INACTIVE" - Ngừng bán).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "combos")
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(150)")
    private String name;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    private BigDecimal originalPrice = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(columnDefinition = "NVARCHAR(255)")
    private String image;

    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ComboItem> getItems() { return items; }
    public void setItems(List<ComboItem> items) {
        this.items.clear();
        if (items != null) {
            items.forEach(this::addItem);
        }
    }

    /**
     * Thêm một thành phần món ăn/uống vào gói combo và thiết lập tham chiếu 2 chiều.
     * 
     * @param item Đối tượng ComboItem.
     */
    public void addItem(ComboItem item) {
        item.setCombo(this);
        this.items.add(item);
    }
}

