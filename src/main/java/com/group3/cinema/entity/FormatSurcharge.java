/**
 * Entity cấu hình Phụ thu định dạng phim / phòng chiếu (`format_surcharges`).
 * 
 * Mã định dạng (`formatCode`): 2D (+0đ), 3D (+20.000đ), IMAX (+50.000đ), Gold (+30.000đ).
 * Dùng để tính tổng tiền vé (`Ticket.formatSurcharge`).
 * 
 * Khởi tạo bởi: TrienLX
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "format_surcharges")
public class FormatSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "format_code", nullable = false, unique = true, length = 20, columnDefinition = "NVARCHAR(20)")
    private String formatCode;

    @Column(name = "surcharge_amount", nullable = false)
    private double surchargeAmount;

    public FormatSurcharge() {}

    public FormatSurcharge(String formatCode, double surchargeAmount) {
        this.formatCode = formatCode;
        this.surchargeAmount = surchargeAmount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFormatCode() { return formatCode; }
    public void setFormatCode(String formatCode) { this.formatCode = formatCode; }

    public double getSurchargeAmount() { return surchargeAmount; }
    public void setSurchargeAmount(double surchargeAmount) { this.surchargeAmount = surchargeAmount; }
}

