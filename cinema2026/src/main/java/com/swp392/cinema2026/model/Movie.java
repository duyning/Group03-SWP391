package com.swp392.cinema2026.model;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: Movie.java
 * Chức năng: Thực thể JPA (Entity) ánh xạ tới bảng "movies" trong cơ sở dữ liệu.
 *            Đại diện cho thông tin một bộ phim gồm có: tiêu đề, trailer (link YouTube hoặc video đã tải lên),
 *            tóm tắt, thể loại, thời lượng, đạo diễn, ngôn ngữ, diễn viên, ngày phát hành và trạng thái.
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import jakarta.persistence.*;
import java.time.LocalDate;

// Khai báo đây là một thực thể JPA (Entity) để ánh xạ với bảng trong cơ sở dữ liệu
@Entity
// Định nghĩa tên bảng tương ứng trong cơ sở dữ liệu là "movies"
@Table(name = "movies")
public class Movie {

    // Khai báo khóa chính (Primary Key) cho bảng movies
    @Id
    // Thiết lập khóa chính tự động tăng (Identity) trong cơ sở dữ liệu
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Trường tên phim tiếng Việt, bắt buộc phải có (không được phép null)
    @Column(nullable = false)
    private String title;

    // Đường dẫn (URL) liên kết xem video Trailer trên YouTube của phim
    private String trailerUrl;

    // Trường tóm tắt nội dung phim, lưu với kiểu dữ liệu NVARCHAR(MAX) trong SQL Server để hỗ trợ văn bản dài tiếng Việt
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String summary;

    // Thể loại của bộ phim (ví dụ: Hành động, Tình cảm...)
    private String genre;

    // Thời lượng của bộ phim tính bằng phút
    private Integer duration;

    // Tên đạo diễn hoặc tác giả thực hiện bộ phim
    private String director;

    // Định dạng và ngôn ngữ hiển thị của phim (ví dụ: 2D Phụ đề, 3D Thuyết minh...)
    private String language;

    // Danh sách tên các diễn viên chính tham gia phim, hỗ trợ văn bản dài tiếng Việt
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String actors;

    // Đường dẫn (URL) ảnh đại diện/Poster của bộ phim để hiển thị lên giao diện
    private String posterUrl;

    // Ngày bộ phim bắt đầu được khởi chiếu tại rạp
    private LocalDate releaseDate;

    // Trạng thái hiển thị của bộ phim tại rạp (ví dụ: "Đang chiếu", "Sắp chiếu", "Suất chiếu đặc biệt")
    private String status;

    // Khởi tạo constructor không tham số mặc định (bắt buộc đối với JPA Entity)
    public Movie() {
    }

    // Khởi tạo constructor đầy đủ tham số để tạo nhanh đối tượng Movie mới
    public Movie(Long id, String title, String trailerUrl, String summary, String genre, Integer duration,
                 String director, String language, String actors, String posterUrl, LocalDate releaseDate, String status) {
        this.id = id;
        this.title = title;
        this.trailerUrl = trailerUrl;
        this.summary = summary;
        this.genre = genre;
        this.duration = duration;
        this.director = director;
        this.language = language;
        this.actors = actors;
        this.posterUrl = posterUrl;
        this.releaseDate = releaseDate;
        this.status = status;
    }

    // Lấy giá trị ID của bộ phim
    public Long getId() {
        return id;
    }

    // Gán giá trị ID cho bộ phim
    public void setId(Long id) {
        this.id = id;
    }

    // Lấy tên phim (Tiếng Việt)
    public String getTitle() {
        return title;
    }

    // Thiết lập tên phim (Tiếng Việt)
    public void setTitle(String title) {
        this.title = title;
    }

    // Lấy link xem video Trailer YouTube
    public String getTrailerUrl() {
        return trailerUrl;
    }

    // Thiết lập link xem video Trailer YouTube
    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }

    // Lấy thông tin tóm tắt phim
    public String getSummary() {
        return summary;
    }

    // Thiết lập thông tin tóm tắt phim
    public void setSummary(String summary) {
        this.summary = summary;
    }

    // Lấy thể loại phim
    public String getGenre() {
        return genre;
    }

    // Thiết lập thể loại phim
    public void setGenre(String genre) {
        this.genre = genre;
    }

    // Lấy thời lượng phim (phút)
    public Integer getDuration() {
        return duration;
    }

    // Thiết lập thời lượng phim (phút)
    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    // Lấy tên đạo diễn/tác giả phim
    public String getDirector() {
        return director;
    }

    // Thiết lập tên đạo diễn/tác giả phim
    public void setDirector(String director) {
        this.director = director;
    }

    // Lấy ngôn ngữ/định dạng phim
    public String getLanguage() {
        return language;
    }

    // Thiết lập ngôn ngữ/định dạng phim
    public void setLanguage(String language) {
        this.language = language;
    }

    // Lấy danh sách diễn viên phim
    public String getActors() {
        return actors;
    }

    // Thiết lập danh sách diễn viên phim
    public void setActors(String actors) {
        this.actors = actors;
    }

    // Lấy đường dẫn ảnh poster phim
    public String getPosterUrl() {
        return posterUrl;
    }

    // Thiết lập đường dẫn ảnh poster phim
    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    // Lấy ngày khởi chiếu phim
    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    // Thiết lập ngày khởi chiếu phim
    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    // Lấy trạng thái hiển thị phim
    public String getStatus() {
        return status;
    }

    // Thiết lập trạng thái hiển thị phim
    public void setStatus(String status) {
        this.status = status;
    }

    // Danh sách lịch chiếu liên kết với bộ phim này (Xóa phim sẽ tự động xóa tất cả lịch chiếu liên quan)
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore // Ngăn chặn vòng lặp vô hạn khi chuyển đổi đối tượng sang JSON
    private java.util.List<Showtime> showtimes = new java.util.ArrayList<>();

    // Lấy danh sách lịch chiếu của bộ phim
    public java.util.List<Showtime> getShowtimes() {
        return showtimes;
    }

    // Gán danh sách lịch chiếu cho bộ phim
    public void setShowtimes(java.util.List<Showtime> showtimes) {
        this.showtimes = showtimes;
    }
}
