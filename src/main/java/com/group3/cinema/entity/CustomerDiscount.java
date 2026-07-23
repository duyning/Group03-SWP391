/**
 * Entity cấu hình Mức giảm giá theo loại Khách hàng (`customer_discounts`).
 * 
 * Áp dụng cho các nhóm đối tượng:
 * - STUDENT (Sinh viên/Học sinh), CHILD (Trẻ em), ELDERLY (Người cao tuổi), ADULT (Người lớn).
 * - Lưu tỷ lệ chiết khấu (`discountRate`: 0.10, 0.20), giá vé đồng giá ngày thường (`fixedPriceWeekday`),
 *   giá vé tối thiểu áp dụng (`minPriceToApply`) và số tiền giảm tối đa (`maxDiscountAmount`).
 * 
 * Khởi tạo bởi: TrienLX (25/06/2026)
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
    private String customerType;

    @Column(name = "discount_rate", nullable = false)
    private double discountRate;

    @Column(name = "fixed_price_weekday")
    private Double fixedPriceWeekday;

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

