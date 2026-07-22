/**
 * Entity cấu hình Phụ phí theo loại ghế (`seat_type_surcharges`).
 * 
 * Mã loại ghế (`seatTypeCode`): std (+0đ), vip (+15.000đ), couple (+30.000đ).
 * Dùng để tính phụ thu loại ghế khi tạo vé xem phim (`Ticket.seatSurcharge`).
 * 
 * Khởi tạo bởi: TrienLX (25/06/2026)
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "seat_type_surcharges")
public class SeatTypeSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_type_code", nullable = false, unique = true, length = 30, columnDefinition = "NVARCHAR(30)")
    private String seatTypeCode;

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

