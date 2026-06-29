package com.group3.cinema.entity;

/*
 * Entity quản lý vé xem phim.
 * Created/updated by: NinhDD - HE186113, TrienLX
 */

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(name = "room_name", columnDefinition = "NVARCHAR(100)")
    private String roomName;

    @Column(name = "seat_label", columnDefinition = "NVARCHAR(20)")
    private String seatLabel;

    @Column(name = "seat_number", columnDefinition = "NVARCHAR(20)")
    private String seatNumber;

    @Column(name = "seat_type", columnDefinition = "NVARCHAR(30)")
    private String seatType = "std";

    @Column(name = "show_date")
    private LocalDate showDate;

    @Column(name = "show_time")
    private LocalTime showTime;

    @Column(name = "base_price")
    private double basePrice;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "booking_time")
    private LocalDateTime bookingTime;

    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "CONFIRMED";

    @Column(name = "customer_type", columnDefinition = "NVARCHAR(30)")
    private String customerType = "ADULT";

    @Column(name = "payment_method", columnDefinition = "NVARCHAR(50)")
    private String paymentMethod;

    @Column(name = "booking_code", columnDefinition = "NVARCHAR(50)")
    private String bookingCode;

    public Ticket() {
    }

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

    public Showtime getShowtime() {
        return showtime;
    }

    public void setShowtime(Showtime showtime) {
        this.showtime = showtime;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
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
        if (this.seatNumber == null || this.seatNumber.isBlank()) {
            this.seatNumber = seatLabel;
        }
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
        if (this.seatLabel == null || this.seatLabel.isBlank()) {
            this.seatLabel = seatNumber;
        }
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

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price != null ? price.doubleValue() : 0.0;
    }

    public BigDecimal getPriceAsBigDecimal() {
        return BigDecimal.valueOf(price);
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

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
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

    public String getStatusDisplayName() {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case "CONFIRMED" -> "Đã xác nhận";
            case "USED" -> "Đã sử dụng";
            case "CANCELLED" -> "Đã hủy";
            case "Còn trống" -> "Còn trống";
            case "Đã bán" -> "Đã bán";
            default -> status;
        };
    }

    public String getSeatTypeDisplayName() {
        if (seatType == null || seatType.isBlank()) {
            return "Thường";
        }
        String normalized = seatType.trim().toLowerCase();
        return switch (normalized) {
            case "vip" -> "VIP";
            case "couple", "đôi", "doi" -> "Ghế đôi";
            case "broken", "hỏng", "hong" -> "Ghế hỏng";
            case "empty", "trống", "trong" -> "Lối đi / Trống";
            default -> "Thường";
        };
    }
}
