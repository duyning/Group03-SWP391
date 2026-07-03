/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: Showtime.java
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-23
 * Chi tiết thay đổi:
 * - Thêm chú thích @JsonFormat cho showDate và showTime để định dạng chuẩn JSON string khi trả về API.
 * - Thêm trường 'note' để đánh dấu suất chiếu đã được điều chỉnh riêng lẻ (override) so với lịch gốc.
 * - [SỬA - TrienLX - 2026-06-23] Thêm trường is_override (boolean) để phân biệt rõ ràng
 *   suất chiếu đã điều chỉnh riêng khỏi suất gốc trong dải, thay thế cách dùng note cũ.
 */
package com.group3.cinema.entity;

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

    /*
     * Ghi chú đặc biệt cho suất chiếu (ví dụ: "Đã điều chỉnh" khi sửa 1 ngày riêng lẻ trong nhóm).
     * null = suất chiếu bình thường; có giá trị = suất chiếu đã được điều chỉnh riêng.
     */
    @Column(columnDefinition = "NVARCHAR(100)")
    private String note;

    /*
     * [SUA - TrienLX - 2026-06-23]
     * Cờ hiệu biểu thị suất chiếu này đã được điều chỉnh riêng khỏi dải lịch gốc.
     * false = suất chiếu bình thường theo lịch tạo ban đầu.
     * true  = Admin đã chỉnh sửa thủ công giờ chiếu/phòng cho riêng ngày này.
     * Dùng cợ này để Frontend hiển thị badge "Dã điều chỉnh" và backend tránh ghi đè khi cập nhật nhóm.
     */
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

    // [SUA - TrienLX - 2026-06-23]: getter/setter cho isOverride
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
