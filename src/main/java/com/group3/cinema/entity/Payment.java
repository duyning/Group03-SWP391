package com.group3.cinema.entity;

/*
 * Added on 2026-06-24: Stores payment transaction state for customer bookings.
 * Updated on 2026-06-26: payOS remains the active customer payment method.
 * Created by: HuyPB - HE191335
 */

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "orderCode"))
public class Payment {
    public enum Status { PENDING, SUCCESS, FAILED, CANCELLED }
    public enum Method { VNPAY, MOMO, PAYOS }
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
