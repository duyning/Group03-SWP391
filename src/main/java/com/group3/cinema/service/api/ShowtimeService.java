package com.group3.cinema.service.api;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeService.java
 * Chức năng: Xử lý nghiệp vụ lịch chiếu: CRUD, lọc, phân loại ngày, chống trùng phòng
 *            theo khoảng thời gian và tạo lịch hàng loạt theo dải ngày.
 * Người viết: Group 03 - SWP391
 * Người sửa: TrienLX, NinhDD
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ShowtimeService {

    private static final int DEFAULT_MOVIE_DURATION_MINUTES = 120;
    private static final int ROOM_TURNOVER_MINUTES = 30;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;

    @Autowired
    public ShowtimeService(ShowtimeRepository showtimeRepository, MovieRepository movieRepository) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
    }

    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAll();
    }

    public Optional<Showtime> getShowtimeById(Long id) {
        return showtimeRepository.findById(id);
    }

    public List<Showtime> searchShowtimes(Integer movieId, String dayType, LocalDate startDate, LocalDate endDate) {
        return showtimeRepository.searchShowtimes(movieId, dayType, startDate, endDate);
    }

    @Transactional
    public Showtime saveShowtime(Showtime showtime) {
        prepareAndValidateShowtime(showtime, null);
        showtime.setDayType(determineDayType(showtime.getShowDate()));
        return showtimeRepository.save(showtime);
    }

    @Transactional
    public Showtime updateShowtime(Long id, Showtime updatedShowtime) {
        return showtimeRepository.findById(id).map(showtime -> {
            prepareAndValidateShowtime(updatedShowtime, id);
            showtime.setMovie(updatedShowtime.getMovie());
            showtime.setShowDate(updatedShowtime.getShowDate());
            showtime.setShowTime(updatedShowtime.getShowTime());
            showtime.setRoom(updatedShowtime.getRoom());
            showtime.setDayType(determineDayType(updatedShowtime.getShowDate()));
            return showtimeRepository.save(showtime);
        }).orElseThrow(() -> new RuntimeException("Showtime not found with id " + id));
    }

    @Transactional
    public List<Showtime> updateShowtimeBatch(Long id, com.group3.cinema.controller.api.ShowtimeController.ShowtimeRequest req) {
        if (req.getGroupIds() != null && !req.getGroupIds().isEmpty()) {
            for (Long oldId : req.getGroupIds()) {
                if (showtimeRepository.existsById(oldId)) {
                    showtimeRepository.deleteById(oldId);
                }
            }
        } else if (showtimeRepository.existsById(id)) {
            showtimeRepository.deleteById(id);
        }

        showtimeRepository.flush();

        return saveShowtimeBatch(
                req.getMovieId(),
                req.getStartDate(),
                req.getEndDate() != null ? req.getEndDate() : req.getStartDate(),
                req.getShowTime(),
                req.getRoom(),
                1
        );
    }

    @Transactional
    public List<Showtime> saveShowtimeBatch(Long movieId, LocalDate startDate, LocalDate endDate,
                                            LocalTime showTime, String room, Integer slotCount) {
        if (movieId == null || startDate == null || endDate == null || showTime == null || room == null || room.isBlank()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ thông tin lịch chiếu.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        int count = slotCount != null ? slotCount : 1;
        if (count < 1 || count > 15) {
            throw new IllegalArgumentException("Số suất chiếu trong ngày phải từ 1 đến 15.");
        }

        Movie movie = movieRepository.findById(movieId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Phim được chọn không tồn tại."));
        int duration = resolveDuration(movie);
        List<Showtime> savedShowtimes = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            LocalTime currentSlotTime = showTime;
            LocalDate slotDate = current;

            for (int i = 0; i < count; i++) {
                Showtime showtime = new Showtime();
                showtime.setMovie(movie);
                showtime.setShowDate(slotDate);
                showtime.setShowTime(currentSlotTime);
                showtime.setRoom(room.trim());
                showtime.setDayType(determineDayType(slotDate));

                validateDateTimeNotPast(slotDate, currentSlotTime);
                validateRoomTimeOverlap(showtime, null);
                savedShowtimes.add(showtimeRepository.save(showtime));

                LocalTime nextStart = currentSlotTime.plusMinutes(duration + ROOM_TURNOVER_MINUTES);
                int remainder = nextStart.getMinute() % 5;
                if (remainder > 0) {
                    nextStart = nextStart.plusMinutes(5 - remainder);
                }
                if (nextStart.isBefore(currentSlotTime)) {
                    slotDate = slotDate.plusDays(1);
                }
                currentSlotTime = nextStart;
            }

            current = current.plusDays(1);
        }

        return savedShowtimes;
    }

    private void prepareAndValidateShowtime(Showtime showtime, Long editingId) {
        if (showtime.getMovie() == null || showtime.getMovie().getId() <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn phim chiếu.");
        }
        if (showtime.getShowDate() == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày chiếu.");
        }
        if (showtime.getShowTime() == null) {
            throw new IllegalArgumentException("Vui lòng chọn giờ chiếu.");
        }
        if (showtime.getRoom() == null || showtime.getRoom().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn phòng chiếu.");
        }

        Movie movie = movieRepository.findById(showtime.getMovie().getId())
                .orElseThrow(() -> new IllegalArgumentException("Phim được chọn không tồn tại."));
        showtime.setMovie(movie);
        showtime.setRoom(showtime.getRoom().trim());
        validateDateTimeNotPast(showtime.getShowDate(), showtime.getShowTime());
        validateRoomTimeOverlap(showtime, editingId);
    }

    private void validateDateTimeNotPast(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (date.isBefore(today)) {
            throw new IllegalArgumentException("Không thể xếp lịch chiếu cho ngày trong quá khứ.");
        }
        if (date.isEqual(today) && time.isBefore(now)) {
            throw new IllegalArgumentException("Không thể xếp lịch chiếu cho giờ chiếu đã qua hôm nay.");
        }
    }

    private void validateRoomTimeOverlap(Showtime candidate, Long editingId) {
        int candidateStart = toMinutes(candidate.getShowTime());
        int candidateEnd = candidateStart + resolveDuration(candidate.getMovie()) + ROOM_TURNOVER_MINUTES;

        List<Showtime> sameRoomShowtimes = showtimeRepository.findByRoomIgnoreCaseAndShowDate(
                candidate.getRoom(),
                candidate.getShowDate()
        );

        for (Showtime existing : sameRoomShowtimes) {
            if (editingId != null && editingId.equals(existing.getId())) {
                continue;
            }

            int existingStart = toMinutes(existing.getShowTime());
            int existingEnd = existingStart + resolveDuration(existing.getMovie()) + ROOM_TURNOVER_MINUTES;

            if (candidateStart < existingEnd && candidateEnd > existingStart) {
                String movieTitle = existing.getMovie() != null ? existing.getMovie().getTitle() : "suất chiếu khác";
                LocalTime existingEndTime = existing.getShowTime().plusMinutes(resolveDuration(existing.getMovie()) + ROOM_TURNOVER_MINUTES);
                throw new IllegalArgumentException(
                        "Phòng " + candidate.getRoom()
                                + " đã có lịch chiếu \"" + movieTitle + "\" từ "
                                + existing.getShowTime().format(TIME_FORMATTER) + " đến "
                                + existingEndTime.format(TIME_FORMATTER)
                                + ". Vui lòng chọn phòng hoặc khung giờ khác."
                );
            }
        }
    }

    private int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private int resolveDuration(Movie movie) {
        if (movie == null || movie.getDuration() == null || movie.getDuration() <= 0) {
            return DEFAULT_MOVIE_DURATION_MINUTES;
        }
        return movie.getDuration();
    }

    public void deleteShowtime(Long id) {
        showtimeRepository.deleteById(id);
    }

    public Map<String, Long> getShowtimeStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", showtimeRepository.count());
        stats.put("weekday", showtimeRepository.countByDayType("Trong tuần"));
        stats.put("weekend", showtimeRepository.countByDayType("Cuối tuần"));
        stats.put("holiday", showtimeRepository.countByDayType("Ngày lễ"));
        return stats;
    }

    public String determineDayType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if ((month == 1 && day == 1) || (month == 4 && day == 30)
                || (month == 5 && day == 1) || (month == 9 && day == 2)) {
            return "Ngày lễ";
        }

        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "Cuối tuần";
        }

        return "Trong tuần";
    }
}
