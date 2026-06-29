package com.group3.cinema.controller.api;

/*
 * REST Controller quản lý lịch chiếu.
 * Created/updated by: TrienLX - HE182285, NinhDD - HE186113
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.service.api.ShowtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/showtimes")
@CrossOrigin(origins = "*")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @Autowired
    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    public static class ShowtimeRequest {
        private Long movieId;
        private Movie movie;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate showDate;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime showTime;

        private String room;
        private Integer slotCount;
        private List<Long> groupIds;

        public Long getMovieId() { return movieId; }
        public void setMovieId(Long movieId) { this.movieId = movieId; }

        public Movie getMovie() { return movie; }
        public void setMovie(Movie movie) { this.movie = movie; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public LocalDate getShowDate() { return showDate; }
        public void setShowDate(LocalDate showDate) { this.showDate = showDate; }

        public LocalTime getShowTime() { return showTime; }
        public void setShowTime(LocalTime showTime) { this.showTime = showTime; }

        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }

        public Integer getSlotCount() { return slotCount; }
        public void setSlotCount(Integer slotCount) { this.slotCount = slotCount; }

        public List<Long> getGroupIds() { return groupIds; }
        public void setGroupIds(List<Long> groupIds) { this.groupIds = groupIds; }
    }

    public static class OverrideDayRequest {
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

    @GetMapping
    public ResponseEntity<List<Showtime>> searchShowtimes(
            @RequestParam(value = "movieId", required = false) Integer movieId,
            @RequestParam(value = "dayType", required = false) String dayType,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(showtimeService.searchShowtimes(movieId, dayType, startDate, endDate));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getShowtimeStats() {
        return ResponseEntity.ok(showtimeService.getShowtimeStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Showtime> getShowtimeById(@PathVariable("id") Long id) {
        return showtimeService.getShowtimeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createShowtime(@RequestBody ShowtimeRequest req) {
        try {
            Long movieId = resolveMovieId(req);
            LocalDate startDate = resolveStartDate(req);

            if (req.getEndDate() != null || req.getSlotCount() != null || req.getStartDate() != null) {
                List<Showtime> saved = showtimeService.saveShowtimeBatch(
                        movieId,
                        startDate,
                        req.getEndDate() != null ? req.getEndDate() : startDate,
                        req.getShowTime(),
                        req.getRoom(),
                        req.getSlotCount()
                );
                return ResponseEntity.ok(saved);
            }

            Showtime showtime = toShowtime(req);
            return ResponseEntity.ok(showtimeService.saveShowtime(showtime));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Lỗi hệ thống: " + ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowtime(@PathVariable("id") Long id, @RequestBody ShowtimeRequest req) {
        try {
            if (req.getGroupIds() != null || req.getStartDate() != null || req.getEndDate() != null) {
                req.setMovieId(resolveMovieId(req));
                req.setStartDate(resolveStartDate(req));
                if (req.getEndDate() == null) {
                    req.setEndDate(req.getStartDate());
                }
                return ResponseEntity.ok(showtimeService.updateShowtimeBatch(id, req));
            }

            return ResponseEntity.ok(showtimeService.updateShowtime(id, toShowtime(req)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Lỗi hệ thống: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShowtime(@PathVariable("id") Long id) {
        try {
            showtimeService.deleteShowtime(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Lỗi hệ thống khi điều chỉnh ngày chiếu: " + ex.getMessage()));
        }
    }

    private Showtime toShowtime(ShowtimeRequest req) {
        Showtime showtime = new Showtime();
        Long movieId = resolveMovieId(req);
        if (movieId != null) {
            Movie movie = new Movie();
            movie.setId(movieId.intValue());
            showtime.setMovie(movie);
        }
        showtime.setShowDate(resolveStartDate(req));
        showtime.setShowTime(req.getShowTime());
        showtime.setRoom(req.getRoom());
        return showtime;
    }

    private Long resolveMovieId(ShowtimeRequest req) {
        if (req.getMovieId() != null) {
            return req.getMovieId();
        }
        if (req.getMovie() != null && req.getMovie().getId() > 0) {
            return (long) req.getMovie().getId();
        }
        return null;
    }

    private LocalDate resolveStartDate(ShowtimeRequest req) {
        return req.getStartDate() != null ? req.getStartDate() : req.getShowDate();
    }

    private Map<String, String> errorBody(String message) {
        return Map.of("message", message, "error", message);
    }
}
