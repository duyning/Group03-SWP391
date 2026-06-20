package com.group3.cinema.controller.api;

/**
 * Dá»± Ã¡n: Cinema 2026 â€” SWP391 Group 03
 * File: ShowtimeController.java
 * Chá»©c nÄƒng: REST Controller cung cáº¥p cÃ¡c API Ä‘á»ƒ quáº£n lÃ½ lá»‹ch chiáº¿u (Showtime).
 *            Há»— trá»£ xem danh sÃ¡ch, lá»c lá»‹ch chiáº¿u nÃ¢ng cao (theo phim, loáº¡i ngÃ y, khoáº£ng ngÃ y),
 *            táº¡o má»›i, cáº­p nháº­t, xÃ³a lá»‹ch chiáº¿u vÃ  thá»‘ng kÃª sá»‘ lÆ°á»£ng lá»‹ch chiáº¿u theo loáº¡i ngÃ y.
 * Endpoints:
 *   - GET /api/showtimes: Láº¥y danh sÃ¡ch lá»‹ch chiáº¿u theo bá»™ lá»c (Query Params: movieId, dayType, startDate, endDate).
 *   - GET /api/showtimes/{id}: Xem thÃ´ng tin chi tiáº¿t má»™t lá»‹ch chiáº¿u theo ID.
 *   - POST /api/showtimes: Táº¡o lá»‹ch chiáº¿u má»›i (bao gá»“m tá»± Ä‘á»™ng Ä‘á»‹nh cáº¥u hÃ¬nh giÃ¡ vÃ©).
 *   - PUT /api/showtimes/{id}: Cáº­p nháº­t lá»‹ch chiáº¿u.
 *   - DELETE /api/showtimes/{id}: XÃ³a lá»‹ch chiáº¿u.
 *   - GET /api/showtimes/stats: Thá»‘ng kÃª sá»‘ lá»‹ch chiáº¿u (Tá»•ng sá»‘, trong tuáº§n, cuá»‘i tuáº§n, ngÃ y lá»…).
 * NgÆ°á»i viáº¿t: TrienLX - HE182285
 * NgÃ y táº¡o: 2026-06-04
 */

import com.group3.cinema.entity.Showtime;
import com.group3.cinema.service.api.ShowtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// ÄÃ¡nh dáº¥u Ä‘Ã¢y lÃ  má»™t RestController quáº£n lÃ½ API cho Showtime
@RestController
@RequestMapping("/api/showtimes")
// Cho phÃ©p gá»i API tá»« giao diá»‡n HTML tÄ©nh á»Ÿ nguá»“n khÃ¡c (CORS)
@CrossOrigin(origins = "*")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @Autowired
    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    // Endpoint: GET /api/showtimes
    // Há»— trá»£ tÃ¬m kiáº¿m, lá»c danh sÃ¡ch lá»‹ch chiáº¿u theo phim, loáº¡i ngÃ y hoáº·c khoáº£ng ngÃ y chiáº¿u (tuáº§n/thÃ¡ng)
    @GetMapping
    public ResponseEntity<List<Showtime>> searchShowtimes(
            @RequestParam(value = "movieId", required = false) Integer movieId,
            @RequestParam(value = "dayType", required = false) String dayType,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Showtime> showtimes = showtimeService.searchShowtimes(movieId, dayType, startDate, endDate);
        return ResponseEntity.ok(showtimes);
    }

    // Endpoint: GET /api/showtimes/{id}
    // Láº¥y chi tiáº¿t lá»‹ch chiáº¿u phim theo mÃ£ ID
    @GetMapping("/{id}")
    public ResponseEntity<Showtime> getShowtimeById(@PathVariable("id") Long id) {
        return showtimeService.getShowtimeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint: POST /api/showtimes
    // Táº¡o má»›i má»™t lá»‹ch chiáº¿u phim
    @PostMapping
    public ResponseEntity<?> createShowtime(@RequestBody Showtime showtime) {
        try {
            Showtime saved = showtimeService.saveShowtime(showtime);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint: PUT /api/showtimes/{id}
    // Cáº­p nháº­t thÃ´ng tin lá»‹ch chiáº¿u phim
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable("id") Long id, @RequestBody Showtime showtime) {
        try {
            Showtime updated = showtimeService.updateShowtime(id, showtime);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint: DELETE /api/showtimes/{id}
    // XÃ³a lá»‹ch chiáº¿u phim khá»i há»‡ thá»‘ng
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShowtime(@PathVariable("id") Long id) {
        try {
            showtimeService.deleteShowtime(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint: GET /api/showtimes/stats
    // Tráº£ vá» sá»‘ liá»‡u thá»‘ng kÃª tá»•ng há»£p sá»‘ lá»‹ch chiáº¿u theo cÃ¡c loáº¡i ngÃ y
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getShowtimeStats() {
        return ResponseEntity.ok(showtimeService.getShowtimeStats());
    }
}
