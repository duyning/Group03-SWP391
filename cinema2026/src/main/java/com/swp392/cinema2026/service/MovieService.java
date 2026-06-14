package com.swp392.cinema2026.service;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: MovieService.java
 * Chức năng: Lớp nghiệp vụ (Service) xử lý logic kinh doanh cho đối tượng Movie.
 *            Bao gồm các nghiệp vụ: CRUD phim, tìm kiếm & lọc phim nâng cao, và thu thập
 *            thống kê số lượng phim theo trạng thái (Đang chiếu, Sắp chiếu, Suất chiếu đặc biệt).
 * Người viết: Group 03 - SWP391
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-12
 */

import com.swp392.cinema2026.model.Movie;
import com.swp392.cinema2026.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Đánh dấu lớp này là một Service để Spring quản lý và xử lý nghiệp vụ phim
@Service
public class MovieService {

    // Khai báo kết nối đến MovieRepository để thao tác với cơ sở dữ liệu
    private final MovieRepository movieRepository;

    // Tiêm (Inject) MovieRepository qua Constructor Injection
    @Autowired
    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // Lấy danh sách tất cả phim trong cơ sở dữ liệu
    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    // Tìm kiếm phim theo ID (trả về kiểu Optional để tránh lỗi NullPointerException)
    public Optional<Movie> getMovieById(Long id) {
        return movieRepository.findById(id);
    }

    // Thực hiện tìm kiếm và lọc phim thông qua MovieRepository
    public List<Movie> searchMovies(String title, String genre, String director, Integer duration, String status, LocalDate releaseDate) {
        return movieRepository.searchMovies(title, genre, director, duration, status, releaseDate);
    }

    // Tiện ích kiểm tra tính hợp lệ của thông tin phim
    private void validateMovie(Movie movie) {
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            throw new IllegalArgumentException("Tên phim không được để trống!");
        }
        if (movie.getDuration() == null || movie.getDuration() < 30 || movie.getDuration() > 300) {
            throw new IllegalArgumentException("Thời lượng phim phải từ 30 đến 300 phút!");
        }
        if (movie.getDirector() == null || movie.getDirector().isBlank()) {
            throw new IllegalArgumentException("Đạo diễn không được để trống!");
        }
        if (movie.getLanguage() == null || movie.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Ngôn ngữ không được để trống!");
        }
        if (movie.getActors() == null || movie.getActors().isBlank()) {
            throw new IllegalArgumentException("Diễn viên không được để trống!");
        }
        if (movie.getPosterUrl() == null || movie.getPosterUrl().isBlank()) {
            throw new IllegalArgumentException("Poster phim không được để trống!");
        }
        if (movie.getTrailerUrl() == null || movie.getTrailerUrl().isBlank()) {
            throw new IllegalArgumentException("Trailer phim không được để trống!");
        }
        if (movie.getReleaseDate() == null) {
            throw new IllegalArgumentException("Ngày khởi chiếu không được để trống!");
        }
        // Chặn ngày khởi chiếu trong quá khứ khi thêm mới/sửa
        if (movie.getReleaseDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày khởi chiếu không được là ngày trong quá khứ!");
        }
        if (movie.getStatus() == null || movie.getStatus().isBlank()) {
            throw new IllegalArgumentException("Trạng thái không được để trống!");
        }
        if (movie.getReleaseYear() == null || movie.getReleaseYear() < 1800 || movie.getReleaseYear() > 2100) {
            throw new IllegalArgumentException("Năm phát hành phải từ năm 1800 đến 2100!");
        }
        if (movie.getProducer() == null || movie.getProducer().isBlank()) {
            throw new IllegalArgumentException("Nhà sản xuất không được để trống!");
        }
        if (movie.getAgeRating() == null || movie.getAgeRating().isBlank()) {
            throw new IllegalArgumentException("Khuyến cáo độ tuổi không được để trống!");
        }
    }

    // Hàm tự động xác định trạng thái phim dựa trên ngày khởi chiếu
    private String resolveStatusFromDate(LocalDate releaseDate, String currentStatus) {
        if ("Suất chiếu đặc biệt".equals(currentStatus)) {
            return currentStatus;
        }
        if (releaseDate == null) {
            return "Sắp chiếu";
        }
        if (releaseDate.isAfter(LocalDate.now())) {
            return "Sắp chiếu";
        } else {
            return "Đang chiếu";
        }
    }

    // Lưu một bộ phim mới hoặc lưu thay đổi vào cơ sở dữ liệu
    @Transactional
    public Movie saveMovie(Movie movie) {
        validateMovie(movie);

        // Kiểm tra trùng lặp tên phim
        if (movieRepository.existsByTitleIgnoreCase(movie.getTitle())) {
            throw new IllegalArgumentException("Tên phim đã tồn tại!");
        }
        // Kiểm tra trùng lặp URL trailer
        if (movieRepository.existsByTrailerUrl(movie.getTrailerUrl())) {
            throw new IllegalArgumentException("Trailer phim này đã được sử dụng cho một phim khác!");
        }
        // Kiểm tra trùng lặp URL poster
        if (movieRepository.existsByPosterUrl(movie.getPosterUrl())) {
            throw new IllegalArgumentException("Poster phim đã tồn tại!");
        }

        // Tự động tính trạng thái từ ngày khởi chiếu
        movie.setStatus(resolveStatusFromDate(movie.getReleaseDate(), movie.getStatus()));

        return movieRepository.save(movie);
    }

    // Cập nhật thông tin của một bộ phim đã tồn tại
    @Transactional
    public Movie updateMovie(Long id, Movie updatedMovie) {
        validateMovie(updatedMovie);

        // Kiểm tra trùng lặp tên phim (loại trừ chính nó)
        if (movieRepository.existsByTitleIgnoreCaseAndIdNot(updatedMovie.getTitle(), id)) {
            throw new IllegalArgumentException("Tên phim đã tồn tại!");
        }
        // Kiểm tra trùng lặp URL trailer (loại trừ chính nó)
        if (movieRepository.existsByTrailerUrlAndIdNot(updatedMovie.getTrailerUrl(), id)) {
            throw new IllegalArgumentException("Trailer phim này đã được sử dụng cho một phim khác!");
        }
        // Kiểm tra trùng lặp URL poster (loại trừ chính nó)
        if (movieRepository.existsByPosterUrlAndIdNot(updatedMovie.getPosterUrl(), id)) {
            throw new IllegalArgumentException("Poster phim đã tồn tại!");
        }

        return movieRepository.findById(id).map(movie -> {
            // Thiết lập giá trị mới cho từng thuộc tính của phim
            movie.setTitle(updatedMovie.getTitle());
            movie.setTrailerUrl(updatedMovie.getTrailerUrl());
            movie.setSummary(updatedMovie.getSummary());
            movie.setGenre(updatedMovie.getGenre());
            movie.setDuration(updatedMovie.getDuration());
            movie.setDirector(updatedMovie.getDirector());
            movie.setLanguage(updatedMovie.getLanguage());
            movie.setActors(updatedMovie.getActors());
            movie.setPosterUrl(updatedMovie.getPosterUrl());
            movie.setReleaseDate(updatedMovie.getReleaseDate());
            movie.setReleaseYear(updatedMovie.getReleaseYear());
            movie.setProducer(updatedMovie.getProducer());
            movie.setAgeRating(updatedMovie.getAgeRating());
            // Ghi trạng thái đã được tính tự động
            movie.setStatus(resolveStatusFromDate(updatedMovie.getReleaseDate(), updatedMovie.getStatus()));
            // Lưu lại phim đã cập nhật vào cơ sở dữ liệu
            return movieRepository.save(movie);
        }).orElseThrow(() -> new RuntimeException("Movie not found with id " + id)); // Ném ngoại lệ nếu không tìm thấy phim
    }

    // Xóa bộ phim khỏi cơ sở dữ liệu theo ID
    public void deleteMovie(Long id) {
        movieRepository.deleteById(id);
    }

    // Lấy số liệu thống kê tổng hợp số lượng phim theo từng trạng thái hiển thị
    public Map<String, Long> getMovieStats() {
        Map<String, Long> stats = new HashMap<>();
        // Đếm tổng số phim trong hệ thống
        stats.put("total", movieRepository.count());
        // Đếm số lượng phim đang chiếu
        stats.put("nowShowing", movieRepository.countByStatus("Đang chiếu"));
        // Đếm số lượng phim sắp chiếu
        stats.put("upcoming", movieRepository.countByStatus("Sắp chiếu"));
        // Đếm số lượng phim suất chiếu đặc biệt
        stats.put("specialShow", movieRepository.countByStatus("Suất chiếu đặc biệt"));
        return stats;
    }
}
