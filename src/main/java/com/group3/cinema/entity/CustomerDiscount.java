/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: CustomerDiscount.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Định nghĩa mức chiết khấu theo loại đối tượng (Sinh viên, Trẻ em, Người lớn...).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_discounts")
public class CustomerDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_type", nullable = false, unique = true, length = 30, columnDefinition = "NVARCHAR(30)")
    private String customerType; // "STUDENT", "CHILD", "ELDERLY", "ADULT"

    @Column(name = "discount_rate", nullable = false)
    private double discountRate; // 0.10, 0.20, 0.30

    @Column(name = "fixed_price_weekday")
    private Double fixedPriceWeekday; // Đồng giá ngày thường (nếu có, e.g., 55000.0)

    @Column(name = "min_price_to_apply", nullable = false)
    private double minPriceToApply = 0.0;

    @Column(name = "max_discount_amount", nullable = false)
    private double maxDiscountAmount = 999999.0;

    public CustomerDiscount() {
    }

    public CustomerDiscount(String customerType, double discountRate, Double fixedPriceWeekday) {
        this.customerType = customerType;
        this.discountRate = discountRate;
        this.fixedPriceWeekday = fixedPriceWeekday;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public double getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(double discountRate) {
        this.discountRate = discountRate;
    }

    public Double getFixedPriceWeekday() {
        return fixedPriceWeekday;
    }

    public void setFixedPriceWeekday(Double fixedPriceWeekday) {
        this.fixedPriceWeekday = fixedPriceWeekday;
    }

    public double getMinPriceToApply() {
        return minPriceToApply;
    }

    public void setMinPriceToApply(double minPriceToApply) {
        this.minPriceToApply = minPriceToApply;
    }

    public double getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(double maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }
}
