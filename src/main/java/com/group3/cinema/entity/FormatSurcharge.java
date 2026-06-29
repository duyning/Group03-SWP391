/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: FormatSurcharge.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Định nghĩa phụ phí định dạng phòng chiếu (2D: 0, 3D: +25.000đ, IMAX: +80.000đ).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "format_surcharges")
public class FormatSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "format_code", nullable = false, unique = true, length = 20)
    private String formatCode; // "2D", "3D", "IMAX", "Premium"

    @Column(name = "surcharge_amount", nullable = false)
    private double surchargeAmount;

    public FormatSurcharge() {
    }

    public FormatSurcharge(String formatCode, double surchargeAmount) {
        this.formatCode = formatCode;
        this.surchargeAmount = surchargeAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFormatCode() {
        return formatCode;
    }

    public void setFormatCode(String formatCode) {
        this.formatCode = formatCode;
    }

    public double getSurchargeAmount() {
        return surchargeAmount;
    }

    public void setSurchargeAmount(double surchargeAmount) {
        this.surchargeAmount = surchargeAmount;
    }
}
