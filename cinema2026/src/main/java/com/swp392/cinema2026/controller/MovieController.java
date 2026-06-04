package com.swp392.cinema2026.controller;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: MovieController.java
 * Chức năng: REST Controller cung cấp các API để quản lý phim (Movie).
 *            Hỗ trợ hiển thị danh sách, tìm kiếm & lọc nâng cao, thêm mới, sửa đổi, xóa phim và
 *            thống kê tổng số lượng phim theo từng trạng thái.
 * Endpoints:
 *   - GET /api/movies: Lấy danh sách phim theo bộ lọc (Query Params: title, genre, director, duration, status, releaseDate).
 *   - GET /api/movies/{id}: Xem thông tin chi tiết của một bộ phim theo ID.
 *   - POST /api/movies: Thêm phim mới.
 *   - PUT /api/movies/{id}: Cập nhật thông tin phim.
 *   - DELETE /api/movies/{id}: Xóa một bộ phim.
 *   - GET /api/movies/stats: Thống kê số lượng phim (tổng số, đang chiếu, sắp chiếu, suất đặc biệt).
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import com.swp392.cinema2026.model.Movie;
import com.swp392.cinema2026.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Đánh dấu lớp này là một RestController để Spring Boot định cấu hình các API endpoint trả về dữ liệu JSON
@RestController
// Định nghĩa đường dẫn cơ sở (base path) cho toàn bộ API trong controller này là "/api/movies"
@RequestMapping("/api/movies")
// Cho phép tất cả các nguồn gốc (Cross-Origin Resource Sharing) gọi đến API này (để tránh lỗi CORS)
@CrossOrigin(origins = "*")
public class MovieController {

    // Khai báo kết nối đến MovieService xử lý nghiệp vụ
    private final MovieService movieService;

    // Tiêm (Inject) MovieService thông qua Constructor Injection
    @Autowired
    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    // Endpoint: GET /api/movies
    // Hỗ trợ tìm kiếm, lọc phim nâng cao bằng cách truyền tham số tùy chọn (Query Parameters)
    @GetMapping
    public ResponseEntity<List<Movie>> searchMovies(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "director", required = false) String director,
            @RequestParam(value = "duration", required = false) Integer duration,
            @RequestParam(value = "status", required = false) String status,
            // Hỗ trợ parse định dạng ngày chuẩn ISO (yyyy-MM-dd) thành kiểu dữ liệu LocalDate
            @RequestParam(value = "releaseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releaseDate
    ) {
        List<Movie> movies = movieService.searchMovies(title, genre, director, duration, status, releaseDate);
        return ResponseEntity.ok(movies); // Trả về mã phản hồi HTTP 200 OK kèm danh sách phim
    }

    // Endpoint: GET /api/movies/{id}
    // Lấy thông tin chi tiết của một bộ phim theo mã ID truyền vào từ đường dẫn (Path Variable)
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable Long id) {
        return movieService.getMovieById(id)
                .map(ResponseEntity::ok) // Nếu tìm thấy, trả về HTTP 200 OK kèm dữ liệu phim
                .orElse(ResponseEntity.notFound().build()); // Nếu không tìm thấy, trả về HTTP 404 Not Found
    }

    // Endpoint: POST /api/movies
    // Thêm một bộ phim mới vào cơ sở dữ liệu. Dữ liệu phim được gửi trong thân Request (Request Body) ở dạng JSON
    @PostMapping
    public ResponseEntity<Movie> createMovie(@RequestBody Movie movie) {
        Movie savedMovie = movieService.saveMovie(movie);
        return ResponseEntity.ok(savedMovie); // Trả về HTTP 200 OK kèm đối tượng phim đã được lưu thành công
    }

    // Endpoint: PUT /api/movies/{id}
    // Cập nhật thông tin phim đã tồn tại theo mã ID
    @PutMapping("/{id}")
    public ResponseEntity<Movie> updateMovie(@PathVariable Long id, @RequestBody Movie movie) {
        try {
            Movie updated = movieService.updateMovie(id, movie);
            return ResponseEntity.ok(updated); // Trả về HTTP 200 OK kèm thông tin phim sau khi sửa đổi thành công
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // Trả về HTTP 404 Not Found nếu ID phim không tồn tại
        }
    }

    // Endpoint: DELETE /api/movies/{id}
    // Xóa bộ phim khỏi hệ thống theo mã ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMovie(@PathVariable Long id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.noContent().build(); // Trả về HTTP 204 No Content nếu xóa thành công
        } catch (Exception e) {
            return ResponseEntity.notFound().build(); // Trả về HTTP 404 Not Found nếu gặp lỗi
        }
    }

    // Endpoint: GET /api/movies/stats
    // Trả về số liệu thống kê tổng số lượng phim và phim theo từng trạng thái hiển thị
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getMovieStats() {
        return ResponseEntity.ok(movieService.getMovieStats()); // Trả về HTTP 200 OK kèm map dữ liệu thống kê dạng JSON
    }
}
