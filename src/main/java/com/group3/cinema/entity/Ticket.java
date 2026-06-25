package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entity đại diện cho vé xem phim (Ticket) trong hệ thống.
 * Lưu trữ thông tin đặt vé bao gồm phim, ghế, phòng chiếu, thời gian, giá vé và trạng thái.
 *
 * Ngày thực hiện: 26/06/2026
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Người mua vé */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** Phim đã đặt */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    /** Tên phòng chiếu, ví dụ: "Phòng 01", "Cinema 01" */
    @Column(name = "room_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String roomName;

    /** Nhãn ghế hiển thị, ví dụ: "A5", "B3-B4" */
    @Column(name = "seat_label", nullable = false, columnDefinition = "NVARCHAR(20)")
    private String seatLabel;

    /** Loại ghế: "std", "vip", "couple" */
    @Column(name = "seat_type", nullable = false, columnDefinition = "NVARCHAR(30)")
    private String seatType = "std";

    /** Ngày chiếu */
    @Column(name = "show_date", nullable = false)
    private LocalDate showDate;

    /** Giờ chiếu */
    @Column(name = "show_time", nullable = false)
    private LocalTime showTime;

    /** Giá vé */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** Thời gian đặt vé */
    @Column(name = "booking_time", nullable = false)
    private LocalDateTime bookingTime;

    /**
     * Trạng thái vé:
     * - "CONFIRMED" : Đã xác nhận
     * - "USED"      : Đã sử dụng
     * - "CANCELLED" : Đã hủy
     */
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "CONFIRMED";

    /** Phương thức thanh toán: "Momo", "ZaloPay", "VNPay", "Tiền mặt" */
    @Column(name = "payment_method", columnDefinition = "NVARCHAR(50)")
    private String paymentMethod;

    /** Mã đặt vé duy nhất, ví dụ: "CF-20260625-001" */
    @Column(name = "booking_code", nullable = false, unique = true, columnDefinition = "NVARCHAR(50)")
    private String bookingCode;

    public Ticket() {
    }

    // ===== Getters and Setters =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getSeatLabel() {
        return seatLabel;
    }

    public void setSeatLabel(String seatLabel) {
        this.seatLabel = seatLabel;
    }

    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }

    public LocalDate getShowDate() {
        return showDate;
    }

    public void setShowDate(LocalDate showDate) {
        this.showDate = showDate;
    }

    public LocalTime getShowTime() {
        return showTime;
    }

    public void setShowTime(LocalTime showTime) {
        this.showTime = showTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(LocalDateTime bookingTime) {
        this.bookingTime = bookingTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    /**
     * Lấy tên hiển thị trạng thái bằng tiếng Việt.
     */
    public String getStatusDisplayName() {
        if (status == null) return "Không xác định";
        return switch (status) {
            case "CONFIRMED" -> "Đã xác nhận";
            case "USED" -> "Đã sử dụng";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    /**
     * Lấy tên hiển thị loại ghế bằng tiếng Việt.
     */
    public String getSeatTypeDisplayName() {
        if (seatType == null) return "Thường";
        return switch (seatType) {
            case "vip" -> "VIP";
            case "couple" -> "Ghế đôi";
            default -> "Thường";
        };
    }
}
