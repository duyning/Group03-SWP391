package com.group3.cinema.controller.api;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeController.java
 * Chức năng: REST Controller cung cấp các API để quản lý lịch chiếu (Showtime).
 *            Hỗ trợ xem danh sách, lọc lịch chiếu nâng cao (theo phim, loại ngày, khoảng ngày),
 *            tạo mới (đơn lẻ hoặc hàng loạt theo dải ngày), cập nhật, xóa lịch chiếu,
 *            và thống kê số lượng lịch chiếu theo loại ngày.
 * Endpoints:
 *   - GET  /api/showtimes             : Lấy danh sách lịch chiếu theo bộ lọc.
 *   - GET  /api/showtimes/{id}        : Xem chi tiết một lịch chiếu theo ID.
 *   - POST /api/showtimes             : Tạo lịch chiếu (hỗ trợ thêm hàng loạt theo dải ngày).
 *   - PUT  /api/showtimes/{id}        : Cập nhật lịch chiếu.
 *   - DELETE /api/showtimes/{id}      : Xóa lịch chiếu.
 *   - GET  /api/showtimes/stats       : Thống kê lịch chiếu.
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-23
 * Chi tiết thay đổi:
 *   - [SỬA - TrienLX - 2026-06-23] Thêm API POST /api/showtimes/override-day để điều chỉnh lịch chiếu cho một ngày cụ thể.
 *   - [SỬA - TrienLX - 2026-06-23] Cập nhật OverrideDayRequest: thêm originalShowtimeId để
 *     override chính xác bản ghi gốc thay vì tạo bản ghi mới (sửa lỗi trùng suất chiếu).
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.api.ShowtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
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

    // DTO (Data Transfer Object) dùng riêng cho yêu cầu tạo lịch chiếu hàng loạt
    public static class ShowtimeRequest {
        private Long movieId;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime showTime;

        private String room;

        // Số suất chiếu trong ngày (tùy chọn, mặc định 1)
        private Integer slotCount;

        private List<Long> groupIds;

        public Long getMovieId() { return movieId; }
        public void setMovieId(Long movieId) { this.movieId = movieId; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public LocalTime getShowTime() { return showTime; }
        public void setShowTime(LocalTime showTime) { this.showTime = showTime; }

        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }

        public Integer getSlotCount() { return slotCount; }
        public void setSlotCount(Integer slotCount) { this.slotCount = slotCount; }

        public List<Long> getGroupIds() { return groupIds; }
        public void setGroupIds(List<Long> groupIds) { this.groupIds = groupIds; }
    }

    // Endpoint: GET /api/showtimes
    // Hỗ trợ tìm kiếm, lọc danh sách lịch chiếu theo phim, loại ngày hoặc khoảng ngày chiếu
    @GetMapping
    public ResponseEntity<List<Showtime>> searchShowtimes(
            @RequestParam(value = "movieId", required = false) Integer movieId,
            @RequestParam(value = "dayType", required = false) String dayType,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(showtimeService.searchShowtimes(movieId, dayType, startDate, endDate));
    }

    // Endpoint: GET /api/showtimes/stats
    // Trả về thống kê số lịch chiếu theo tổng số và phân loại ngày
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getShowtimeStats() {
        return ResponseEntity.ok(showtimeService.getShowtimeStats());
    }

    // Endpoint: GET /api/showtimes/{id}
    // Xem chi tiết một lịch chiếu theo ID
    @GetMapping("/{id}")
    public ResponseEntity<Showtime> getShowtimeById(@PathVariable("id") Long id) {
        return showtimeService.getShowtimeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint: POST /api/showtimes
    // Tạo lịch chiếu mới — hỗ trợ cả thêm đơn lẻ và thêm hàng loạt theo dải ngày.
    @PostMapping
    public ResponseEntity<?> createShowtime(@RequestBody ShowtimeRequest req) {
        try {
            if (req.getMovieId() != null && req.getStartDate() != null) {
                // Chế độ thêm hàng loạt theo dải ngày
                List<Showtime> saved = showtimeService.saveShowtimeBatch(
                        req.getMovieId(),
                        req.getStartDate(),
                        req.getEndDate() != null ? req.getEndDate() : req.getStartDate(),
                        req.getShowTime(),
                        req.getRoom(),
                        req.getSlotCount()
                );
                return ResponseEntity.ok(saved);
            } else {
                // Chế độ đơn (backward-compatible)
                Showtime st = new Showtime();
                if (req.getMovieId() != null) {
                    Movie mv = new Movie();
                    mv.setId(req.getMovieId().intValue());
                    st.setMovie(mv);
                }
                st.setShowDate(req.getStartDate());
                st.setShowTime(req.getShowTime());
                st.setRoom(req.getRoom());
                return ResponseEntity.ok(showtimeService.saveShowtime(st));
            }
        } catch (IllegalArgumentException ex) {
            // Xung đột lịch chiếu hoặc ngày/giờ không hợp lệ → trả về 400
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Lỗi hệ thống: " + ex.getMessage()));
        }
    }

    // Endpoint: PUT /api/showtimes/{id}
    // Cập nhật thông tin lịch chiếu hiện có
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable("id") Long id, @RequestBody ShowtimeRequest req) {
        try {
            return ResponseEntity.ok(showtimeService.updateShowtimeBatch(id, req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Lỗi hệ thống: " + ex.getMessage()));
        }
    }

    // Endpoint: DELETE /api/showtimes/{id}
    // Xóa lịch chiếu
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShowtime(@PathVariable("id") Long id) {
        try {
            showtimeService.deleteShowtime(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint: POST /api/showtimes/override-day
    // [SỬA - TrienLX - 2026-06-23]: Điều chỉnh giờ chiếu của 1 ngày cụ thể trong một nhóm lịch chiếu.
    // Cập nhật bản ghi gốc theo originalShowtimeId (hoặc tìm theo movieId+date) thay vì tạo mới.
    @PostMapping("/override-day")
    public ResponseEntity<?> overrideSingleDay(@RequestBody OverrideDayRequest req) {
        try {
            Showtime result = showtimeService.overrideSingleDay(
                    req.getOriginalShowtimeId(),
                    req.getMovieId(),
                    req.getTargetDate(),
                    req.getNewShowTime(),
                    req.getRoom()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Lỗi hệ thống khi điều chỉnh ngày chiếu: " + ex.getMessage()));
        }
    }

    // DTO: OverrideDayRequest
    // [SỬA - TrienLX - 2026-06-23]: Thêm trường originalShowtimeId để frontend truyền ID bản ghi gốc,
    // giúp backend tìm chính xác suất cần điều chỉnh mà không tạo bản ghi trùng lặp.
    public static class OverrideDayRequest {
        // ID của suất chiếu gốc cần chỉnh sửa (tùy chọn nhưng quan trọng với độ chính xác)
        private Long originalShowtimeId;

        private Long movieId;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate targetDate;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime newShowTime;

        private String room;

        public Long getOriginalShowtimeId() { return originalShowtimeId; }
        public void setOriginalShowtimeId(Long originalShowtimeId) { this.originalShowtimeId = originalShowtimeId; }

        public Long getMovieId() { return movieId; }
        public void setMovieId(Long movieId) { this.movieId = movieId; }

        public LocalDate getTargetDate() { return targetDate; }
        public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

        public LocalTime getNewShowTime() { return newShowTime; }
        public void setNewShowTime(LocalTime newShowTime) { this.newShowTime = newShowTime; }

        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }
    }
}
