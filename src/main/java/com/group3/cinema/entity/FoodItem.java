/**
 * Entity lưu trữ Danh mục Sản phẩm Đồ ăn / Thức uống đơn lẻ (`food_items`).
 * 
 * Chức năng:
 * - Lưu tên mặt hàng (`name`), phân loại (`category`: Bắp ngô, Nước ngọt, Đồ ăn vặt), đơn giá bán lẻ (`unitPrice`), giá vốn nhập (`costPrice`), mô tả (`description`).
 * - Trạng thái kinh doanh (`status`: ACTIVE - Đang kinh doanh, INACTIVE - Tạm ngưng).
 * - Được ghép thành phần trong các gói Combo (`ComboItem`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (21/06/2026)
 */
package com.group3.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "food_items")
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(150)")
    private String name;

    @Column(nullable = false, columnDefinition = "NVARCHAR(80)")
    private String category;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(columnDefinition = "NVARCHAR(255)")
    private String description;

    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "ACTIVE";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

