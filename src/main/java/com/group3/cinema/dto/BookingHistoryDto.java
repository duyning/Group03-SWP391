package com.group3.cinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO chi chua du lieu ma booking-history.html can hien thi.
public class BookingHistoryDto {
    // Ma giao dich tu Payment.orderCode hoac ma du phong CF-{bookingId}.
    private String bookingCode;
    // Thoi diem Booking duoc tao.
    private LocalDateTime bookingTime;
    // Tong so tien cua Booking.
    private BigDecimal totalAmount;
    // Ten phuong thuc thanh toan da chuyen thanh chuoi.
    private String paymentMethod;
    // Nhan trang thai hien thi cho user.
    private String status;
    // Cau tom tat gom so ve, phim va danh sach ghe.
    private String summary;
    
    // Thuộc tính phụ để CSS badge
    // Ten CSS class dung de to mau badge trang thai.
    private String statusClass;

    // Constructor rong de service co the tao DTO roi gan tung field.
    public BookingHistoryDto() {
    }

    // Tra ma giao dich cho Thymeleaf qua ${item.bookingCode}.
    public String getBookingCode() {
        return bookingCode;
    }

    // Gan ma giao dich khi PaymentService tao DTO.
    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    // Tra thoi gian giao dich cho template format.
    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    // Gan thoi gian giao dich.
    public void setBookingTime(LocalDateTime bookingTime) {
        this.bookingTime = bookingTime;
    }

    // Tra tong tien cho template format tien te.
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    // Gan tong tien cua booking.
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    // Tra ten phuong thuc thanh toan.
    public String getPaymentMethod() {
        return paymentMethod;
    }

    // Gan ten phuong thuc thanh toan.
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    // Tra nhan trang thai giao dich.
    public String getStatus() {
        return status;
    }

    // Gan nhan trang thai giao dich.
    public void setStatus(String status) {
        this.status = status;
    }

    // Tra cau tom tat cua giao dich.
    public String getSummary() {
        return summary;
    }

    // Gan cau tom tat cua giao dich.
    public void setSummary(String summary) {
        this.summary = summary;
    }

    // Tra CSS class cho badge trang thai.
    public String getStatusClass() {
        return statusClass;
    }

    // Gan CSS class do PaymentService chon.
    public void setStatusClass(String statusClass) {
        this.statusClass = statusClass;
    }
}
