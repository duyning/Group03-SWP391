/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: Ticket.java
 * Người tạo: TrienLX
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "showtime_id", nullable = false)
    @JsonIgnoreProperties({"movie", "room", "dayType", "note", "isOverride", "active"})
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "seat_type", nullable = false, length = 30)
    private String seatType;   // "Thường" | "VIP" | "Đôi"

    @Column(name = "base_price", nullable = false)
    private double basePrice;  // Giá gốc (ADULT, trước giảm giá)

    @Column(name = "price", nullable = false)
    private double price;      // Giá sau khi áp dụng chiết khấu (để tương thích ngược)

    @Column(name = "seat_surcharge", nullable = false)
    private double seatSurcharge;  // Phụ thu loại ghế

    @Column(name = "format_surcharge", nullable = false)
    private double formatSurcharge;  // Phụ thu định dạng phim

    @Column(name = "discount_amount", nullable = false)
    private double discountAmount;  // Số tiền giảm giá

    @Column(name = "final_price", nullable = false)
    private double finalPrice;  // Giá cuối cùng khách phải trả

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();  // Thời điểm bán vé

    @Column(name = "customer_name", length = 255)
    private String customerName;  // Tên khách hàng (tùy chọn)

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;  // Số điện thoại khách hàng (tùy chọn)

    @Column(name = "status", nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "BOOKED";   // "BOOKED" | "PENDING" | "REFUNDED"

    @Column(name = "customer_type", columnDefinition = "NVARCHAR(30)")
    private String customerType = "ADULT"; // "ADULT" | "STUDENT" | "CHILD" | "VIP"

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    public Ticket() {}

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Showtime getShowtime() { return showtime; }
    public void setShowtime(Showtime showtime) { this.showtime = showtime; }

    public Seat getSeat() { return seat; }
    public void setSeat(Seat seat) { this.seat = seat; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getSeatType() { return seatType; }
    public void setSeatType(String seatType) { this.seatType = seatType; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public double getSeatSurcharge() { return seatSurcharge; }
    public void setSeatSurcharge(double seatSurcharge) { this.seatSurcharge = seatSurcharge; }

    public double getFormatSurcharge() { return formatSurcharge; }
    public void setFormatSurcharge(double formatSurcharge) { this.formatSurcharge = formatSurcharge; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public double getFinalPrice() { return finalPrice; }
    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
}
