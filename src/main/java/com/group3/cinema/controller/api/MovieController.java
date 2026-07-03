package com.group3.cinema.controller.api;

/**
 * Dá»± Ã¡n: Cinema 2026 â€” SWP391 Group 03
 * File: MovieController.java
 * Chá»©c nÄƒng: REST Controller cung cáº¥p cÃ¡c API Ä‘á»ƒ quáº£n lÃ½ phim (Movie).
 *            Há»— trá»£ hiá»ƒn thá»‹ danh sÃ¡ch, tÃ¬m kiáº¿m & lá» c nÃ¢ng cao, thÃªm má»›i, sá»­a Ä‘á»•i, xÃ³a phim vÃ 
 *            thá»‘ng kÃª tá»•ng sá»‘ lÆ°á»£ng phim theo tá»«ng tráº¡ng thÃ¡i.
 * Endpoints:
 *   - GET /api/movies: Láº¥y danh sÃ¡ch phim theo bá»™ lá» c (Query Params: title, genre, director, duration, status, releaseDate).
 *   - GET /api/movies/{id}: Xem thÃ´ng tin chi tiáº¿t cá»§a má»™t bá»™ phim theo ID.
 *   - POST /api/movies: ThÃªm phim má»›i.
 *   - PUT /api/movies/{id}: Cáº­p nháº­t thÃ´ng tin phim.
 *   - DELETE /api/movies/{id}: XÃ³a má»™t bá»™ phim.
 *   - GET /api/movies/stats: Thá»‘ng kÃª sá»‘ lÆ°á»£ng phim (tá»•ng sá»‘, Ä‘ang chiáº¿u, sáº¯p chiáº¿u, suáº¥t Ä‘áº·c biá»‡t).
 * NgÆ°á» i viáº¿t: TrienLX - HE182285
 * NgÃ y táº¡o: 2026-06-04
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-23
 * Chi tiết thay đổi:
 *   - [SỬA - TrienLX - 2026-06-23] Cải tiến xử lý ngoại lệ trong toggleActive để ghi log lỗi chi tiết và tránh che giấu lỗi hệ thống dưới mã HTTP 404.
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.api.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// ÄÃ¡nh dáº¥u lá»›p nÃ y lÃ  má»™t RestController Ä‘á»ƒ Spring Boot Ä‘á»‹nh cáº¥u hÃ¬nh cÃ¡c API endpoint tráº£ vá» dá»¯ liá»‡u JSON
@RestController("apiMovieController")
// Äá»‹nh nghÄ©a Ä‘Æ°á»ng dáº«n cÆ¡ sá»Ÿ (base path) cho toÃ n bá»™ API trong controller nÃ y lÃ  "/api/movies"
@RequestMapping("/api/movies")
// Cho phÃ©p táº¥t cáº£ cÃ¡c nguá»“n gá»‘c (Cross-Origin Resource Sharing) gá»i Ä‘áº¿n API nÃ y (Ä‘á»ƒ trÃ¡nh lá»—i CORS)
@CrossOrigin(origins = "*")
public class MovieController {

    // Khai bÃ¡o káº¿t ná»‘i Ä‘áº¿n MovieService xá»­ lÃ½ nghiá»‡p vá»¥
    private final MovieService movieService;

    // TiÃªm (Inject) MovieService thÃ´ng qua Constructor Injection
    @Autowired
    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    // Endpoint: GET /api/movies
    // Há»— trá»£ tÃ¬m kiáº¿m, lá»c phim nÃ¢ng cao báº±ng cÃ¡ch truyá»n tham sá»‘ tÃ¹y chá»n (Query Parameters)
    @GetMapping
    public ResponseEntity<List<Movie>> searchMovies(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "director", required = false) String director,
            @RequestParam(value = "duration", required = false) Integer duration,
            @RequestParam(value = "status", required = false) String status,
            // Há»— trá»£ parse Ä‘á»‹nh dáº¡ng ngÃ y chuáº©n ISO (yyyy-MM-dd) thÃ nh kiá»ƒu dá»¯ liá»‡u LocalDate
            @RequestParam(value = "releaseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releaseDate
    ) {
        List<Movie> movies = movieService.searchMovies(title, genre, director, duration, status, releaseDate);
        return ResponseEntity.ok(movies); // Tráº£ vá» mÃ£ pháº£n há»“i HTTP 200 OK kÃ¨m danh sÃ¡ch phim
    }

    // Endpoint: GET /api/movies/{id}
    // Láº¥y thÃ´ng tin chi tiáº¿t cá»§a má»™t bá»™ phim theo mÃ£ ID truyá»n vÃ o tá»« Ä‘Æ°á»ng dáº«n (Path Variable)
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable("id") Integer id) {
        return movieService.getMovieById(id)
                .map(ResponseEntity::ok) // Náº¿u tÃ¬m tháº¥y, tráº£ vá»  HTTP 200 OK kÃ¨m dá»¯ liá»‡u phim
                .orElse(ResponseEntity.notFound().build()); // Náº¿u khÃ´ng tÃ¬m tháº¥y, tráº£ vá»  HTTP 404 Not Found
    }

    // Endpoint: POST /api/movies
    // Thêm một bộ phim mới vào cơ sở dữ liệu. Dữ liệu phim được gửi trong thân Request (Request Body) ở dạng JSON
    @PostMapping
    public ResponseEntity<?> createMovie(@RequestBody Movie movie) {
        try {
            Movie savedMovie = movieService.saveMovie(movie);
            return ResponseEntity.ok(savedMovie); // Trả về HTTP 200 OK kèm đối tượng phim đã được lưu thành công
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint: PUT /api/movies/{id}
    // Cập nhật thông tin phim đã tồn tại theo mã ID
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMovie(@PathVariable("id") Integer id, @RequestBody Movie movie) {
        try {
            Movie updated = movieService.updateMovie(id, movie);
            return ResponseEntity.ok(updated); // Trả về HTTP 200 OK kèm thông tin phim sau khi sửa đổi thành công
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // Trả về HTTP 404 Not Found nếu ID phim không tồn tại
        }
    }

    // Endpoint: DELETE /api/movies/{id}
    // Xóa bộ phim khỏi hệ thống theo mã ID (Soft-delete: active = false)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMovie(@PathVariable("id") Integer id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.ok(Map.of("message", "Phim đã được ẩn thành công."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi hệ thống khi xóa phim."));
        }
    }

    // Endpoint: PATCH /api/movies/{id}/toggle-active
    // Đảo ngược trạng thái hiển thị của phim (active ↔ inactive).
    // Nếu đang hiển thị thì tạm ẩn; nếu đang ẩn thì mở lại — không làm mất dữ liệu.
    // [SỬA - TrienLX - 2026-06-23]: Ghi log lỗi chi tiết khi không thể tạm ẩn/mở lại phim để debug và trả về mã lỗi 500 kèm chi tiết thay vì luôn trả về 404.
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable("id") Integer id) {
        try {
            Movie updated = movieService.toggleActive(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            System.err.println("Lỗi khi toggle-active cho phim ID " + id + ": " + e.getMessage());
            e.printStackTrace();
            if (e.getMessage() != null && e.getMessage().contains("Không tìm thấy phim")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(500)
                    .body(Map.of("message", "Lỗi hệ thống khi tạm ẩn/mở lại phim: " + e.getMessage()));
        }
    }

    // Endpoint: GET /api/movies/stats
    // Tráº£ vá»  sá»‘ liá»‡u thá»‘ng kÃª tá»•ng sá»‘ lÆ°á»£ng phim vÃ  phim theo tá»«ng tráº¡ng thÃ¡i hiá»ƒn thá»‹
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getMovieStats() {
        return ResponseEntity.ok(movieService.getMovieStats()); // Tráº£ vá» HTTP 200 OK kÃ¨m map dá»¯ liá»‡u thá»‘ng kÃª dáº¡ng JSON
    }
}
