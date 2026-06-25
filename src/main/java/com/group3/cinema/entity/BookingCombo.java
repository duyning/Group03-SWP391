package com.group3.cinema.entity;

/*
 * Added on 2026-06-24: Stores combo selections attached to a customer booking.
 * Created by: HuyPB - HE191335
 */

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "booking_combos")
public class BookingCombo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long bookingId;
    @Column(nullable = false) private Long comboId;
    @Column(nullable = false, length = 150) private String comboName;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal;
    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long v) { bookingId = v; }
    public Long getComboId() { return comboId; }
    public void setComboId(Long v) { comboId = v; }
    public String getComboName() { return comboName; }
    public void setComboName(String v) { comboName = v; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { quantity = v; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal v) { unitPrice = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { subtotal = v; }
}
