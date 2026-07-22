/**
 * Entity lưu trữ nhật ký giao dịch thanh toán trực tuyến (`payments`).
 * 
 * Chức năng:
 * - Liên kết tới đơn đặt vé tổng hợp (`bookingId`).
 * - Lưu mã đơn giao dịch gửi tới cổng thanh toán (`orderCode`), mã giao dịch đối tác trả về (`transactionId`),
 *   mã phản hồi (`responseCode`), số tiền (`amount`), thông báo lỗi nếu có (`errorMessage`).
 * - Quản lý phương thức thanh toán (`Method`: VNPAY, MOMO, PAYOS, CASH, CARD, BANK_TRANSFER).
 * - Quản lý trạng thái giao dịch (`Status`: PENDING - Chờ xử lý, SUCCESS - Thành công, FAILED - Thất bại, CANCELLED - Hủy).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "orderCode"))
public class Payment {
    
    /** Enum trạng thái giao dịch thanh toán */
    public enum Status { PENDING, SUCCESS, FAILED, CANCELLED }

    /** Enum các phương thức thanh toán hỗ trợ */
    public enum Method { VNPAY, MOMO, PAYOS, CASH, CARD, BANK_TRANSFER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long bookingId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Method paymentMethod;
    @Column(nullable = false, length = 50) private String orderCode;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(length = 100) private String transactionId;
    @Column(length = 20) private String responseCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 500) private String errorMessage;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long v) { bookingId = v; }
    public Method getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(Method v) { paymentMethod = v; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String v) { orderCode = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { transactionId = v; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String v) { responseCode = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { paidAt = v; }
}

