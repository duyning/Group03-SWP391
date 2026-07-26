/**
 * Entity lưu trạng thái một ghế trong một suất chiếu.
 *
 * Vai trò trong flow bán vé tại quầy:
 * - Khi nhân viên chọn ghế ở `/admin/counter-sales`, `SeatHoldingService.holdSeats(...)`
 *   tạo các bản ghi `BookingTicket` status HOLDING.
 * - Khi nhân viên chốt tiền mặt, `CounterSaleService.completeSale(...)`
 *   gắn `bookingId`, đổi status HOLDING -> BOOKED, xóa holdToken/holdExpiresAt.
 * - Khi nhân viên tạo payOS, `CounterSaleService.createCounterPayment(...)`
 *   gắn `bookingId` nhưng giữ status HOLDING cho tới khi callback thanh toán xác nhận.
 *
 * Cơ chế chống bán trùng ghế:
 * - Unique constraint `(showtime_id, seat_id)` chặn hai bản ghi cho cùng một ghế trong cùng một suất.
 * - Trước khi insert, service vẫn kiểm tra BOOKED/HOLDING để trả thông báo nghiệp vụ dễ hiểu.
 * - Nếu hai request đồng thời vượt qua kiểm tra, DB constraint là lớp bảo vệ cuối.
 *
 * Ý nghĩa field giữ ghế:
 * - `holdToken`: mã phiên giữ ghế của một máy/trình duyệt.
 * - `holdExpiresAt`: thời điểm hết hạn giữ ghế, thường 5 phút.
 * - `bookingId`: null khi chỉ giữ ghế nháp; có giá trị khi đã tạo booking thật.
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_tickets", uniqueConstraints = @UniqueConstraint(columnNames = {"showtime_id", "seat_id"}))
public class BookingTicket {

    /**
     * Trạng thái ghế trong một suất chiếu:
     * - HOLDING: Ghế đang được giữ tạm để checkout hoặc chờ payOS.
     * - BOOKED: Ghế đã thanh toán thành công, không được bán lại.
     */
    public enum Status { HOLDING, BOOKED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "showtime_id", nullable = false) private Long showtimeId;
    @Column(name = "seat_id", nullable = false) private Long seatId;
    private Long bookingId;
    @Column(nullable = false, length = 20) private String seatLabel;
    @Column(nullable = false, length = 30) private String seatType;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 50) private String holdToken;
    private LocalDateTime holdExpiresAt;

    public Long getId() { return id; }
    public Long getShowtimeId() { return showtimeId; }
    public void setShowtimeId(Long value) { this.showtimeId = value; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long value) { this.seatId = value; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long value) { this.bookingId = value; }
    public String getSeatLabel() { return seatLabel; }
    public void setSeatLabel(String value) { this.seatLabel = value; }
    public String getSeatType() { return seatType; }
    public void setSeatType(String value) { this.seatType = value; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal value) { this.price = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { this.status = value; }
    public String getHoldToken() { return holdToken; }
    public void setHoldToken(String value) { this.holdToken = value; }
    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(LocalDateTime value) { this.holdExpiresAt = value; }
}

