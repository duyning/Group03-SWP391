package com.group3.cinema.entity;

/**
 * LUỒNG THỰC THỂ VÉ (ENTITY TICKET):
 * CSDL (Bảng `tickets`) <-> JPA ORM <-> Ticket Entity <-> TicketRepository <-> TicketService
 * 
 * Các thuộc tính nòng cốt:
 * 1. Khóa ngoại liên kết: account (Account), movie (Movie), showtime (Showtime), seat (Seat).
 * 2. Thành phần đơn giá: basePrice, seatSurcharge, formatSurcharge, discountAmount, finalPrice.
 * 3. Trạng thái: status ("CONFIRMED", "BOOKED", "PENDING", "REFUNDED", "USED"). Tự chuyển CONFIRMED -> USED khi qua giờ chiếu.
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
// Map entity vao bang tickets.
@Table(name = "tickets")
public class Ticket {

    // Primary key tu tang cua ve.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Account so huu ve; EAGER de trang My Tickets doc duoc ngay.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Account account;

    // Phim cua ve.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    // Suat chieu cua ve.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "showtime_id")
    // Giu thong tin Showtime nhung tranh serialize lai cac quan he khong can.
    @JsonIgnoreProperties({"movie", "room", "dayType", "note", "override", "active"})
    private Showtime showtime;

    // Ghe da dat.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    // Ten phong duoc luu truc tiep de ve van co snapshot khi du lieu phong thay doi.
    @Column(name = "room_name", columnDefinition = "NVARCHAR(100)")
    private String roomName;

    // Nhan ghe, vi du A5.
    @Column(name = "seat_label", columnDefinition = "NVARCHAR(20)")
    private String seatLabel;

    @Column(name = "seat_number", columnDefinition = "NVARCHAR(20)")
    private String seatNumber;

    @Column(name = "seat_type", columnDefinition = "NVARCHAR(30)")
    private String seatType = "std";

    // Ngay chieu hien tren danh sach va chi tiet ve.
    @Column(name = "show_date")
    private LocalDate showDate;

    // Gio chieu hien tren danh sach va chi tiet ve.
    @Column(name = "show_time")
    private LocalTime showTime;

    @Column(name = "base_price", nullable = false)
    private Double basePrice = 0.0;

    // Gia cuoi cung user da thanh toan.
    @Column(name = "price", nullable = false)
    private Double price = 0.0;

    @Column(name = "seat_surcharge", nullable = false)
    private Double seatSurcharge = 0.0;

    @Column(name = "format_surcharge", nullable = false)
    private Double formatSurcharge = 0.0;

    @Column(name = "discount_amount", nullable = false)
    private Double discountAmount = 0.0;

    @Column(name = "final_price", nullable = false)
    private Double finalPrice = 0.0;

    // Thoi diem dat ve, dung de sap xep ve moi nhat.
    @Column(name = "booking_time")
    private LocalDateTime bookingTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Trang thai luu trong DB; getter co the suy ra USED theo thoi gian chieu.
    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String status = "CONFIRMED";

    @Column(name = "customer_type", columnDefinition = "NVARCHAR(30)")
    private String customerType = "ADULT";

    @Column(name = "customer_name", columnDefinition = "NVARCHAR(100)")
    private String customerName;

    @Column(name = "customer_phone", columnDefinition = "NVARCHAR(20)")
    private String customerPhone;

    // Phuong thuc thanh toan hien tren trang chi tiet.
    @Column(name = "payment_method", columnDefinition = "NVARCHAR(50)")
    private String paymentMethod;

    // Ma booking la du lieu chinh de tao QR check-in.
    @Column(name = "booking_code", columnDefinition = "NVARCHAR(50)")
    private String bookingCode;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

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
        if (showtime != null) {
            this.movie = showtime.getMovie();
            this.roomName = showtime.getRoom();
            this.showDate = showtime.getShowDate();
            this.showTime = showtime.getShowTime();
        }
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
        if (seat != null) {
            setSeatNumber(seat.getSeatLabel());
        }
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
        return basePrice != null ? basePrice : 0.0;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getPrice() {
        return price != null ? price : 0.0;
    }

    public void setPrice(double price) {
        this.price = price;
        if (getFinalPrice() <= 0) {
            this.finalPrice = price;
        }
    }

    public void setPrice(BigDecimal price) {
        setPrice(price != null ? price.doubleValue() : 0.0);
    }

    public BigDecimal getPriceAsBigDecimal() {
        return BigDecimal.valueOf(getPrice());
    }

    public double getSeatSurcharge() {
        return seatSurcharge != null ? seatSurcharge : 0.0;
    }

    public void setSeatSurcharge(double seatSurcharge) {
        this.seatSurcharge = seatSurcharge;
    }

    public double getFormatSurcharge() {
        return formatSurcharge != null ? formatSurcharge : 0.0;
    }

    public void setFormatSurcharge(double formatSurcharge) {
        this.formatSurcharge = formatSurcharge;
    }

    public double getDiscountAmount() {
        return discountAmount != null ? discountAmount : 0.0;
    }

    public void setDiscountAmount(double discountAmount) {
        this.discountAmount = discountAmount;
    }

    public double getFinalPrice() {
        double safeFinalPrice = finalPrice != null ? finalPrice : 0.0;
        return safeFinalPrice > 0 ? safeFinalPrice : getPrice();
    }

    public void setFinalPrice(double finalPrice) {
        this.finalPrice = finalPrice;
    }

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(LocalDateTime bookingTime) {
        this.bookingTime = bookingTime;
        if (this.createdAt == null) {
            this.createdAt = bookingTime;
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Lấy trạng thái của vé.
     * 
     * Tự động kiểm tra thời điểm hiện tại (`LocalDateTime.now()`) so với ngày giờ chiếu (`showDate`, `showTime`).
     * Nếu trạng thái đang là `CONFIRMED` nhưng suất chiếu đã diễn ra -> tự động trả về `USED`.
     * 
     * @return Trạng thái vé hiện tại.
     */
    public String getStatus() {
        // Ve CONFIRMED tu dong hien USED khi suat chieu da qua.
        if ("CONFIRMED".equals(status) && showDate != null && showTime != null) {
            // Ghep ngay va gio thanh mot moc LocalDateTime.
            LocalDateTime showDateTime = LocalDateTime.of(showDate, showTime);
            // So sanh voi thoi diem hien tai.
            if (LocalDateTime.now().isAfter(showDateTime)) {
                return "USED";
            }
        }
        // Cac truong hop khac giu nguyen status trong DB.
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

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getBookingCode() {
        // Thymeleaf dung gia tri nay de tao QR.
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Trả về tên hiển thị tiếng Việt tương ứng cho trạng thái vé phục vụ Thymeleaf UI.
     * 
     * @return Chuỗi tên trạng thái tiếng Việt (Đã xác nhận, Đã sử dụng, Đã hủy, v.v.).
     */
    public String getStatusDisplayName() {
        // Goi getStatus de ap dung quy tac tu CONFIRMED thanh USED.
        String currentStatus = getStatus();
        // Bao ve truong hop du lieu cu khong co status.
        if (currentStatus == null) {
            return "Không xác định";
        }
        // Doi ma status ky thuat thanh nhan tieng Viet.
        return switch (currentStatus) {
            case "CONFIRMED" -> "Đã xác nhận";
            case "USED" -> "Đã sử dụng";
            case "CANCELLED" -> "Đã hủy";
            case "BOOKED" -> "Đã bán";
            case "PENDING" -> "Đang giữ";
            case "REFUNDED" -> "Đã hoàn";
            case "Còn trống" -> "Còn trống";
            case "Đã bán" -> "Đã bán";
            default -> currentStatus;
        };
    }

    /**
     * Trả về tên loại ghế tiếng Việt tương ứng.
     * 
     * @return Tên hiển thị (VIP, Ghế đôi, Thường, v.v.).
     */
    public String getSeatTypeDisplayName() {
        // Loai ghe rong duoc coi la ghe Thuong.
        if (seatType == null || seatType.isBlank()) {
            return "Thường";
        }
        // Chuan hoa de so sanh khong phan biet hoa/thuong va khoang trang.
        String normalized = seatType.trim().toLowerCase();
        // Map cac gia tri ky thuat/du lieu cu thanh nhan tren UI.
        return switch (normalized) {
            case "vip" -> "VIP";
            case "couple", "đôi", "doi" -> "Ghế đôi";
            case "broken", "hỏng", "hong" -> "Ghế hỏng";
            case "empty", "trống", "trong" -> "Lối đi / Trống";
            default -> "Thường";
        };
    }
}

