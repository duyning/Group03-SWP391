package com.swp392.cinema2026.controller;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeController.java
 * Chức năng: REST Controller cung cấp các API để quản lý lịch chiếu (Showtime).
 *            Hỗ trợ xem danh sách, lọc lịch chiếu nâng cao (theo phim, loại ngày, khoảng ngày),
 *            tạo mới, cập nhật, xóa lịch chiếu và thống kê số lượng lịch chiếu theo loại ngày.
 * Endpoints:
 *   - GET /api/showtimes: Lấy danh sách lịch chiếu theo bộ lọc (Query Params: movieId, dayType, startDate, endDate).
 *   - GET /api/showtimes/{id}: Xem thông tin chi tiết một lịch chiếu theo ID.
 *   - POST /api/showtimes: Tạo lịch chiếu mới (bao gồm tự động định cấu hình giá vé).
 *   - PUT /api/showtimes/{id}: Cập nhật lịch chiếu.
 *   - DELETE /api/showtimes/{id}: Xóa lịch chiếu.
 *   - GET /api/showtimes/stats: Thống kê số lịch chiếu (Tổng số, trong tuần, cuối tuần, ngày lễ).
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import com.swp392.cinema2026.model.Showtime;
import com.swp392.cinema2026.service.ShowtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Đánh dấu đây là một RestController quản lý API cho Showtime
@RestController
@RequestMapping("/api/showtimes")
// Cho phép gọi API từ giao diện HTML tĩnh ở nguồn khác (CORS)
@CrossOrigin(origins = "*")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @Autowired
    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    // Endpoint: GET /api/showtimes
    // Hỗ trợ tìm kiếm, lọc danh sách lịch chiếu theo phim, loại ngày hoặc khoảng ngày chiếu (tuần/tháng)
    @GetMapping
    public ResponseEntity<List<Showtime>> searchShowtimes(
            @RequestParam(value = "movieId", required = false) Long movieId,
            @RequestParam(value = "dayType", required = false) String dayType,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Showtime> showtimes = showtimeService.searchShowtimes(movieId, dayType, startDate, endDate);
        return ResponseEntity.ok(showtimes);
    }

    // Endpoint: GET /api/showtimes/{id}
    // Lấy chi tiết lịch chiếu phim theo mã ID
    @GetMapping("/{id}")
    public ResponseEntity<Showtime> getShowtimeById(@PathVariable Long id) {
        return showtimeService.getShowtimeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint: POST /api/showtimes
    // Tạo mới một lịch chiếu phim
    @PostMapping
    public ResponseEntity<Showtime> createShowtime(@RequestBody Showtime showtime) {
        Showtime saved = showtimeService.saveShowtime(showtime);
        return ResponseEntity.ok(saved);
    }

    // Endpoint: PUT /api/showtimes/{id}
    // Cập nhật thông tin lịch chiếu phim
    @PutMapping("/{id}")
    public ResponseEntity<Showtime> updateShowtime(@PathVariable Long id, @RequestBody Showtime showtime) {
        try {
            Showtime updated = showtimeService.updateShowtime(id, showtime);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint: DELETE /api/showtimes/{id}
    // Xóa lịch chiếu phim khỏi hệ thống
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShowtime(@PathVariable Long id) {
        try {
            showtimeService.deleteShowtime(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint: GET /api/showtimes/stats
    // Trả về số liệu thống kê tổng hợp số lịch chiếu theo các loại ngày
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getShowtimeStats() {
        return ResponseEntity.ok(showtimeService.getShowtimeStats());
    }
}
