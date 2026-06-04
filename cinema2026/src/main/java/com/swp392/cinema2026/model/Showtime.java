package com.swp392.cinema2026.model;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: Showtime.java
 * Chức năng: Thực thể JPA (Entity) ánh xạ tới bảng "showtimes" trong cơ sở dữ liệu.
 *            Đại diện cho một lịch chiếu phim, liên kết Many-to-One với thực thể Movie,
 *            bao gồm các thông tin: phim được chiếu, ngày chiếu, giờ chiếu, phòng chiếu, loại ngày và giá vé cơ bản.
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

// Khai báo thực thể JPA ánh xạ đến bảng "showtimes" trong cơ sở dữ liệu
@Entity
@Table(name = "showtimes")
public class Showtime {

    // Khóa chính tự động tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ Nhiều-Một (ManyToOne) với bảng movies. Khi xóa lịch chiếu, bộ phim vẫn được giữ nguyên
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    // Ngày chiếu phim
    @Column(nullable = false)
    private LocalDate showDate;

    // Giờ chiếu phim
    @Column(nullable = false)
    private LocalTime showTime;

    // Phòng chiếu phim (ví dụ: Phòng 1, Phòng 2...)
    @Column(nullable = false)
    private String room;

    // Loại ngày chiếu: "Trong tuần", "Cuối tuần", "Ngày lễ" để phục vụ quản lý phân loại
    private String dayType;

//    // Danh sách vé liên kết với lịch chiếu này (Xóa lịch chiếu tự động xóa tất cả vé liên quan)
//    @OneToMany(mappedBy = "showtime", cascade = CascadeType.ALL, orphanRemoval = true)
//    @com.fasterxml.jackson.annotation.JsonIgnore // Tránh lặp vô hạn JSON
//    private java.util.List<Ticket> tickets = new java.util.ArrayList<>();

    // Constructor mặc định rỗng (bắt buộc cho JPA)
    public Showtime() {
    }

    // Constructor đầy đủ tham số để khởi tạo nhanh đối tượng Showtime (không bao gồm ticketPrice)
    public Showtime(Long id, Movie movie, LocalDate showDate, LocalTime showTime, String room, String dayType) {
        this.id = id;
        this.movie = movie;
        this.showDate = showDate;
        this.showTime = showTime;
        this.room = room;
        this.dayType = dayType;
    }

    // Lấy ID lịch chiếu
    public Long getId() {
        return id;
    }

    // Thiết lập ID lịch chiếu
    public void setId(Long id) {
        this.id = id;
    }

    // Lấy đối tượng phim liên kết
    public Movie getMovie() {
        return movie;
    }

    // Thiết lập đối tượng phim liên kết
    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    // Lấy ngày chiếu phim
    public LocalDate getShowDate() {
        return showDate;
    }

    // Thiết lập ngày chiếu phim
    public void setShowDate(LocalDate showDate) {
        this.showDate = showDate;
    }

    // Lấy giờ chiếu phim
    public LocalTime getShowTime() {
        return showTime;
    }

    // Thiết lập giờ chiếu phim
    public void setShowTime(LocalTime showTime) {
        this.showTime = showTime;
    }

    // Lấy phòng chiếu phim
    public String getRoom() {
        return room;
    }

    // Thiết lập phòng chiếu phim
    public void setRoom(String room) {
        this.room = room;
    }

    // Lấy phân loại loại ngày chiếu
    public String getDayType() {
        return dayType;
    }

    // Thiết lập phân loại loại ngày chiếu
    public void setDayType(String dayType) {
        this.dayType = dayType;
    }

//    // Lấy danh sách các vé của lịch chiếu này
//    public java.util.List<Ticket> getTickets() {
//        return tickets;
//    }
//
//    // Thiết lập danh sách vé của lịch chiếu này
//    public void setTickets(java.util.List<Ticket> tickets) {
//        this.tickets = tickets;
//    }
}
