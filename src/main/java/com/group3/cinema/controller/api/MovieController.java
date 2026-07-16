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
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.service.CustomerNotificationBroadcastService;
import com.group3.cinema.service.api.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Đánh dấu lớp này là một RestController để Spring Boot định cấu hình các API endpoint trả về dữ liệu JSON
@RestController("apiMovieController")
// Định nghĩa đường dẫn cơ sở (base path) cho toàn bộ API trong controller này là "/api/movies"
@RequestMapping("/api/movies")
// Cho phép tất cả các nguồn gốc (Cross-Origin Resource Sharing) gọi đến API này (để tránh lỗi CORS)
@CrossOrigin(origins = "*")
public class MovieController {

    private static final Logger log = LoggerFactory.getLogger(MovieController.class);

    // Khai báo kết nối đến MovieService xử lý nghiệp vụ
    private final MovieService movieService;
    private final com.group3.cinema.repository.api.ShowtimeRepository showtimeRepository;
    private final CustomerNotificationBroadcastService notificationBroadcastService;

    // Tiêm (Inject) MovieService thông qua Constructor Injection
    @Autowired
    public MovieController(MovieService movieService,
                           com.group3.cinema.repository.api.ShowtimeRepository showtimeRepository,
                           CustomerNotificationBroadcastService notificationBroadcastService) {
        this.movieService = movieService;
        this.showtimeRepository = showtimeRepository;
        this.notificationBroadcastService = notificationBroadcastService;
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
    public ResponseEntity<Movie> getMovieById(@PathVariable("id") Integer id) {
        return movieService.getMovieById(id)
                .map(ResponseEntity::ok) // Nếu tìm thấy, trả về HTTP 200 OK kèm dữ liệu phim
                .orElse(ResponseEntity.notFound().build()); // Nếu không tìm thấy, trả về HTTP 404 Not Found
    }

    // Endpoint: POST /api/movies
    // Thêm một bộ phim mới vào cơ sở dữ liệu. Dữ liệu phim được gửi trong thân Request (Request Body) ở dạng JSON
    @PostMapping
    public ResponseEntity<?> createMovie(@RequestBody Movie movie) {
        try {
            Movie savedMovie = movieService.saveMovie(movie);
            notifyCustomersAboutNewMovie(savedMovie);
            return ResponseEntity.ok(savedMovie); // Trả về HTTP 200 OK kèm đối tượng phim đã được lưu thành công
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Không thể thêm phim mới '{}': {}", movie != null ? movie.getTitle() : null, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Không thể lưu phim vào cơ sở dữ liệu. Vui lòng kiểm tra log hệ thống."));
        }
    }

    private void notifyCustomersAboutNewMovie(Movie savedMovie) {
        if (savedMovie == null || !savedMovie.isActive() || savedMovie.getStatus() == Movie.MovieStatus.STOPPED) {
            return;
        }
        try {
            notificationBroadcastService.sendToActiveCustomers(
                    "Phim mới: " + savedMovie.getTitle(),
                    savedMovie.getSummary(),
                    NotificationType.MOVIE,
                    resolveMovieNotificationImage(savedMovie),
                    "/movies/" + savedMovie.getId()
            );
        } catch (Exception exception) {
            log.warn("Phim '{}' đã lưu thành công nhưng không gửi được thông báo: {}",
                    savedMovie.getTitle(), exception.getMessage());
        }
    }

    private String resolveMovieNotificationImage(Movie movie) {
        if (movie.getPosterUrl() != null && !movie.getPosterUrl().isBlank()) {
            return movie.getPosterUrl();
        }
        if (movie.getBannerUrl() != null && !movie.getBannerUrl().isBlank()) {
            return movie.getBannerUrl();
        }
        return null;
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
            java.util.Optional<Movie> movieOpt = movieService.getMovieById(id);
            if (movieOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Movie movie = movieOpt.get();

            // If the movie is currently INACTIVE (about to be activated):
            if (!movie.isActive()) {
                boolean isComingSoonAndNotYetReleased = movie.getStatus() == Movie.MovieStatus.COMING_SOON
                        && (movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(LocalDate.now()));

                if (!isComingSoonAndNotYetReleased) {
                    boolean hasAnyShowtimes = showtimeRepository.countAllShowtimesByMovieId(id) > 0;
                    if (hasAnyShowtimes) {
                        boolean hasFutureShowtimes = showtimeRepository.countFutureShowtimesByMovieId(id, LocalDate.now()) > 0;
                        if (!hasFutureShowtimes) {
                            return ResponseEntity.badRequest().body(Map.of(
                                "message", "Phim đã hết lịch chiếu vui lòng thêm lịch chiếu mới để mở!"
                            ));
                        }
                    }
                }
            }

            Movie updated = movieService.toggleActive(id);
            boolean hasFutureShowtimes = showtimeRepository.countFutureShowtimesByMovieId(id, LocalDate.now()) > 0;
            return ResponseEntity.ok(Map.of(
                "movie", updated,
                "hasFutureShowtimes", hasFutureShowtimes
            ));
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
