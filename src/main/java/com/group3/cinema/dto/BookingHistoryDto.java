/**
 * Đối tượng chuyển đổi dữ liệu Lịch sử Đặt vé Khách hàng (BookingHistoryDto).
 * 
 * Được sử dụng bởi `CustomerBookingService` và `ProfileController` để đóng gói dữ liệu
 * truyền hiển thị trên giao diện `booking-history.html` hoặc `profile.html`.
 */
package com.group3.cinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BookingHistoryDto {
    private String bookingCode;      // Mã đơn đặt vé (ví dụ: BK123456)
    private LocalDateTime bookingTime; // Thời điểm thực hiện đặt vé
    private BigDecimal totalAmount;    // Tổng số tiền thanh toán
    private String paymentMethod;     // Phương thức thanh toán (VNPay, PayOS, Tiền mặt)
    private String status;            // Trạng thái đơn (PAID, PENDING, CANCELLED)
    private String summary;           // Tóm tắt đơn vé (Tên phim, số ghế, combo bắp nước)
    
    // Thuộc tính phụ dùng để gắn lớp CSS badge hiển thị màu sắc trên giao diện Thymeleaf
    private String statusClass;

    /**
     * Constructor mặc định không tham số.
     */
    public BookingHistoryDto() {
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(LocalDateTime bookingTime) {
        this.bookingTime = bookingTime;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatusClass() {
        return statusClass;
    }

    public void setStatusClass(String statusClass) {
        this.statusClass = statusClass;
    }
}

