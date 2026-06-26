/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: Ticket.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Định nghĩa đối tượng Vé phim thực tế gắn với Suất chiếu và Ghế ngồi, lưu trạng thái trống/bán.
 * [SỬA - TrienLX - 2026-06-25] Thêm trường base_price để lưu giá gốc (Adult) trước khi áp chiết khấu,
 *   giúp tính lại giá đúng khi sửa đối tượng khách hàng.
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber; // Sao chép từ Seat.seatLabel để hiển thị nhanh

    @Column(name = "seat_type", nullable = false, length = 30)
    private String seatType;   // Sao chép từ Seat.seatType (std, vip, couple)

    @Column(name = "base_price", nullable = false)
    private double basePrice;  // Giá gốc Adult (trước chiết khấu), dùng để tính lại khi sửa đối tượng

    @Column(name = "price", nullable = false)
    private double price;      // Giá thực tế sau chiết khấu

    @Column(name = "status", nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "Còn trống"; // "Còn trống", "Đã bán"

    @Column(name = "customer_type", columnDefinition = "NVARCHAR(30)")
    private String customerType = "ADULT"; // "ADULT", "STUDENT", "CHILD", "ELDERLY"

    public Ticket() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public void setShowtime(Showtime showtime) {
        this.showtime = showtime;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }
}
