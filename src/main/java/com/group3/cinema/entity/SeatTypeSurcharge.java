/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: SeatTypeSurcharge.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Định nghĩa phụ phí theo loại ghế (Thường: 0, VIP: +15.000đ, Couple: +30.000đ).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "seat_type_surcharges")
public class SeatTypeSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_type_code", nullable = false, unique = true, length = 30)
    private String seatTypeCode; // "std", "vip", "couple"

    @Column(name = "surcharge_amount", nullable = false)
    private double surchargeAmount;

    public SeatTypeSurcharge() {
    }

    public SeatTypeSurcharge(String seatTypeCode, double surchargeAmount) {
        this.seatTypeCode = seatTypeCode;
        this.surchargeAmount = surchargeAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSeatTypeCode() {
        return seatTypeCode;
    }

    public void setSeatTypeCode(String seatTypeCode) {
        this.seatTypeCode = seatTypeCode;
    }

    public double getSurchargeAmount() {
        return surchargeAmount;
    }

    public void setSurchargeAmount(double surchargeAmount) {
        this.surchargeAmount = surchargeAmount;
    }
}
