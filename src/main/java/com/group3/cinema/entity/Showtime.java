package com.group3.cinema.entity;

/**
 * LUỒNG THỰC THỂ SUẤT CHIẾU (ENTITY SHOWTIME):
 * CSDL (Bảng `showtimes`) <-> JPA ORM <-> Showtime Entity <-> ShowtimeRepository <-> ShowtimeService
 * 
 * Các thuộc tính chính:
 * 1. Khóa ngoại: movie (Movie Entity), room (Phòng chiếu).
 * 2. Ngày giờ chiếu: showDate (LocalDate), showTime (LocalTime).
 * 3. Phân loại giá vé: dayType ("Trong tuần", "Cuối tuần", "Ngày lễ").
 * 4. Cờ điều chỉnh: isOverride (true nếu tách sửa ngày chiếu lẻ).
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

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "showtimes")
public class Showtime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate showDate;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm:ss")
    private LocalTime showTime;

    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String room;

    @Column(columnDefinition = "NVARCHAR(20)")
    private String dayType;

    /** Ghi chú đặc biệt cho suất chiếu (ví dụ: "Đã điều chỉnh" khi sửa 1 ngày riêng lẻ trong nhóm) */
    @Column(columnDefinition = "NVARCHAR(100)")
    private String note;

    /** Cờ đánh dấu suất chiếu này đã được Admin điều chỉnh thủ công khỏi dải lịch gốc */
    @Column(name = "is_override", nullable = false)
    private boolean isOverride = false;

    private boolean active = true;

    public Showtime() {
    }

    public Showtime(Long id, Movie movie, LocalDate showDate, LocalTime showTime, String room, String dayType) {
        this.id = id;
        this.movie = movie;
        this.showDate = showDate;
        this.showTime = showTime;
        this.room = room;
        this.dayType = dayType;
        this.note = null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
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

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getDayType() {
        return dayType;
    }

    public void setDayType(String dayType) {
        this.dayType = dayType;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isOverride() {
        return isOverride;
    }

    public void setOverride(boolean isOverride) {
        this.isOverride = isOverride;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

