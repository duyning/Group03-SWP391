package com.group3.cinema.entity;

/*
 * Added on 2026-06-24: Stores held and booked seats for each customer booking.
 * Created by: HuyPB - HE191335
 */

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_tickets", uniqueConstraints = @UniqueConstraint(columnNames = {"showtime_id", "seat_id"}))
public class BookingTicket {
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
