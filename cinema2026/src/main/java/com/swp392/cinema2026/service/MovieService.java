package com.swp392.cinema2026.service;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: MovieService.java
 * Chức năng: Lớp nghiệp vụ (Service) xử lý logic kinh doanh cho đối tượng Movie.
 *            Bao gồm các nghiệp vụ: CRUD phim, tìm kiếm & lọc phim nâng cao, và thu thập
 *            thống kê số lượng phim theo trạng thái (Đang chiếu, Sắp chiếu, Suất chiếu đặc biệt).
 * Người viết: Group 03 - SWP391
 * Ngày tạo: 2026-06-04
 */

import com.swp392.cinema2026.model.Movie;
import com.swp392.cinema2026.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    // Lưu một bộ phim mới hoặc lưu thay đổi vào cơ sở dữ liệu
    public Movie saveMovie(Movie movie) {
        return movieRepository.save(movie);
    }

    // Cập nhật thông tin của một bộ phim đã tồn tại
    public Movie updateMovie(Long id, Movie updatedMovie) {
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
            movie.setStatus(updatedMovie.getStatus());
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
