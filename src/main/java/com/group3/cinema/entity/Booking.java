package com.group3.cinema.entity;

/*
 * Added on 2026-06-24: Stores customer booking header data for ticket purchase flow.
 * Created by: HuyPB - HE191335
 */

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_bookings")
public class Booking {
    public enum Status { PENDING, PAID, CANCELLED, EXPIRED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Integer accountId;
    @Column(nullable = false) private Long showtimeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal ticketSubtotal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal comboSubtotal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(length = 50) private String voucherCode;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public Long getId() { return id; }
    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }
    public Long getShowtimeId() { return showtimeId; }
    public void setShowtimeId(Long showtimeId) { this.showtimeId = showtimeId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public BigDecimal getTicketSubtotal() { return ticketSubtotal; }
    public void setTicketSubtotal(BigDecimal value) { this.ticketSubtotal = value; }
    public BigDecimal getComboSubtotal() { return comboSubtotal; }
    public void setComboSubtotal(BigDecimal value) { this.comboSubtotal = value; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal value) { this.discountAmount = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { this.totalAmount = value; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
