package com.group3.cinema.service.api;

/*
 * Service xử lý nghiệp vụ lịch chiếu.
 * Created/updated by: Group 03 - SWP391, NinhDD - HE186113, TrienLX
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import com.group3.cinema.service.TicketService;
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
    private final TicketService ticketService;
    private final TicketRepository ticketRepository;

    @Autowired
    public ShowtimeService(ShowtimeRepository showtimeRepository,
                           MovieRepository movieRepository,
                           TicketService ticketService,
                           TicketRepository ticketRepository) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
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
        Showtime saved = showtimeRepository.save(showtime);
        ticketService.generateTicketsForShowtime(saved);
        return saved;
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
            Showtime saved = showtimeRepository.save(showtime);
            ticketService.generateTicketsForShowtime(saved);
            return saved;
        }).orElseThrow(() -> new RuntimeException("Showtime not found with id " + id));
    }

    @Transactional
    public List<Showtime> updateShowtimeBatch(Long id, com.group3.cinema.controller.api.ShowtimeController.ShowtimeRequest req) {
        if (req.getGroupIds() != null && !req.getGroupIds().isEmpty()) {
            for (Long oldId : req.getGroupIds()) {
                deleteShowtimeIfSafe(oldId);
            }
        } else {
            deleteShowtimeIfSafe(id);
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

                Showtime saved = showtimeRepository.save(showtime);
                ticketService.generateTicketsForShowtime(saved);
                savedShowtimes.add(saved);

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

    @Transactional
    public Showtime overrideSingleDay(Long originalShowtimeId, Long movieId, LocalDate targetDate,
                                      LocalTime newShowTime, String room) {
        if (targetDate == null || newShowTime == null || room == null || room.isBlank() || movieId == null) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ thông tin để điều chỉnh suất chiếu.");
        }

        validateDateTimeNotPast(targetDate, newShowTime);
        Showtime target;

        if (originalShowtimeId != null) {
            target = showtimeRepository.findById(originalShowtimeId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu gốc có ID = " + originalShowtimeId));
        } else {
            List<Showtime> candidates = showtimeRepository.findByMovieIdAndShowDate(movieId.intValue(), targetDate);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy suất chiếu nào của phim này trong ngày " + targetDate);
            }
            target = candidates.stream()
                    .filter(showtime -> !showtime.isOverride())
                    .findFirst()
                    .orElse(candidates.get(0));
        }

        Movie movie = movieRepository.findById(movieId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Phim được chọn không tồn tại."));
        target.setMovie(movie);
        target.setShowDate(targetDate);
        target.setShowTime(newShowTime);
        target.setRoom(room.trim());
        target.setDayType(determineDayType(targetDate));
        target.setOverride(true);
        target.setNote("Đã điều chỉnh");

        validateRoomTimeOverlap(target, target.getId());

        Showtime saved = showtimeRepository.save(target);
        ticketService.generateTicketsForShowtime(saved);
        return saved;
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

    @Transactional
    public void deleteShowtime(Long id) {
        deleteShowtimeIfSafe(id);
    }

    private void deleteShowtimeIfSafe(Long id) {
        if (id == null || !showtimeRepository.existsById(id)) {
            return;
        }
        boolean hasSold = ticketRepository.existsByShowtimeIdAndStatus(id, "Đã bán");
        if (hasSold) {
            throw new IllegalStateException("Không thể xóa suất chiếu này vì đã có vé bán ra.");
        }
        ticketRepository.deleteUnsoldTicketsByShowtimeId(id);
        showtimeRepository.deleteById(id);
    }

    public Map<String, Long> getShowtimeStats() {
        Map<String, Long> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        stats.put("total", showtimeRepository.count());
        stats.put("active", showtimeRepository.countByShowDate(today));
        stats.put("upcoming", showtimeRepository.countByShowDateGreaterThan(today));
        stats.put("ended", showtimeRepository.countByShowDateLessThan(today));
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
