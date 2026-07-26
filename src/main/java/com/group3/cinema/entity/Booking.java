/**
 * Entity lưu hóa đơn/đơn đặt vé tổng trong bảng `customer_bookings`.
 *
 * Vai trò trong flow bán vé tại quầy:
 * - Tiền mặt:
 *   + `CounterSaleService.completeSale(...)` tạo Booking status PAID ngay.
 *   + `paidAt` được set ngay thời điểm nhân viên nhận tiền.
 *   + Các ghế trong `booking_tickets` chuyển BOOKED.
 * - payOS:
 *   + `CounterSaleService.createCounterPayment(...)` tạo Booking status PENDING.
 *   + `expiresAt` theo thời hạn giữ ghế để nếu khách không thanh toán thì hệ thống có thể giải phóng.
 *   + Khi payment callback thành công, flow payment chung đổi Booking sang PAID.
 *
 * Booking là nơi gom tổng tiền:
 * - `ticketSubtotal`: tổng tiền vé.
 * - `comboSubtotal`: tổng tiền combo bắp nước.
 * - `discountAmount`: số tiền voucher giảm.
 * - `totalAmount`: số tiền cuối cùng phải thu.
 * - `voucherCode`: mã voucher đã áp dụng nếu có.
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_bookings")
public class Booking {
    
    /**
     * Trạng thái đơn:
     * - PENDING: Đã tạo đơn nhưng chưa thanh toán, thường dùng cho payOS.
     * - PAID: Đã thanh toán thành công, dùng cho tiền mặt tại quầy và payOS callback thành công.
     * - CANCELLED: Đã bị hủy.
     * - EXPIRED: Quá thời gian chờ thanh toán/giữ ghế.
     */
    public enum Status { PENDING, PAID, CANCELLED, EXPIRED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Integer accountId;
    @Column(nullable = false) private Long showtimeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal ticketSubtotal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal comboSubtotal;
    @Column(nullable = false, precision = 18, scale = 2,
            columnDefinition = "DECIMAL(18,2) DEFAULT 0")
    private BigDecimal foodSubtotal = BigDecimal.ZERO;
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
    public BigDecimal getFoodSubtotal() { return foodSubtotal == null ? BigDecimal.ZERO : foodSubtotal; }
    public void setFoodSubtotal(BigDecimal value) { this.foodSubtotal = value == null ? BigDecimal.ZERO : value; }
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

