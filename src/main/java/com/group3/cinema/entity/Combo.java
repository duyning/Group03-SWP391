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

    // DÃ¹ng BigDecimal hoáº·c Double Ä‘á»ƒ lÆ°u giÃ¡ tiá»n cho chuáº©n cáº¥u trÃºc sá»‘
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

    // ACTIVE (Äang bÃ¡n) | INACTIVE (Ngá»«ng bÃ¡n)
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboItem> items = new ArrayList<>();

    // ===== Getters and Setters =====
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
    public void addItem(ComboItem item) {
        item.setCombo(this);
        this.items.add(item);
    }
}