package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Showtime selection logic for customer booking.
 * Updated on 2026-06-26: Cinema display data is loaded from SQL booking_settings.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.dto.BookingShowtimeDateView;
import com.group3.cinema.dto.BookingShowtimeView;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.SeatTypeRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookingShowtimeService {

    private static final int DEFAULT_DURATION_MINUTES = 120;
    private static final String ACTIVE_ROOM_STATUS = "Hoạt động";

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final BookingTicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BookingShowtimeService(ShowtimeRepository showtimeRepository,
                                  MovieRepository movieRepository,
                                  RoomRepository roomRepository,
                                  BookingTicketRepository ticketRepository,
                                  SeatRepository seatRepository,
                                  SeatTypeRepository seatTypeRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.seatRepository = seatRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Movie getBookableMovie(int movieId) {
        return movieRepository.findByIdAndActiveTrue(movieId)
                .filter(movie -> movie.getStatus() == Movie.MovieStatus.NOW_SHOWING
                        || movie.getStatus() == Movie.MovieStatus.SPECIAL_SCREENING)
                .orElseThrow(() -> new IllegalArgumentException("Phim không tồn tại hoặc hiện chưa mở bán vé."));
    }

    public List<BookingShowtimeView> getAvailableShowtimes(int movieId, LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Vui lòng chọn ngày chiếu hợp lệ.");
        }
        Movie movie = getBookableMovie(movieId);
        LocalDateTime now = LocalDateTime.now();

        return showtimeRepository.searchShowtimesForCustomer(movieId, null, date, date).stream()
                .filter(showtime -> LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime()).isAfter(now))
                .map(showtime -> toView(showtime, movie))
                .filter(view -> view != null && view.availableSeatCount() > 0)
                .toList();
    }

    public List<BookingShowtimeDateView> getAvailableShowtimeSchedule(int movieId) {
        Movie movie = getBookableMovie(movieId);
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(30);
        LocalDateTime now = LocalDateTime.now();
        Map<LocalDate, List<BookingShowtimeView>> grouped = new LinkedHashMap<>();

        showtimeRepository.searchShowtimesForCustomer(movieId, null, today, maxDate).stream()
                .filter(showtime -> LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime()).isAfter(now))
                .map(showtime -> toView(showtime, movie))
                .filter(view -> view != null && view.availableSeatCount() > 0)
                .forEach(view -> grouped.computeIfAbsent(view.showDate(), ignored -> new java.util.ArrayList<>()).add(view));

        return grouped.entrySet().stream()
                .map(entry -> new BookingShowtimeDateView(entry.getKey(), dayOfWeekLabel(entry.getKey()), entry.getValue()))
                .toList();
    }

    public String getCinemaName() {
        List<String> values = jdbcTemplate.query(
                "SELECT setting_value FROM booking_settings WHERE setting_key = 'cinema_name'",
                (rs, rowNum) -> rs.getString("setting_value")
        );
        return values.isEmpty() ? "" : values.get(0);
    }

    public BookingSelection validateAndCreateSelection(long showtimeId, int movieId, LocalDate date) {
        if (showtimeId <= 0 || movieId <= 0 || date == null) {
            throw new IllegalArgumentException("Thông tin phim, ngày hoặc suất chiếu chưa đầy đủ.");
        }

        Movie movie = getBookableMovie(movieId);
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Suất chiếu không tồn tại."));
        if (showtime.getMovie() == null || showtime.getMovie().getId() != movieId
                || !date.equals(showtime.getShowDate())) {
            throw new IllegalArgumentException("Suất chiếu không khớp với phim và ngày đã chọn.");
        }
        if (!LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime()).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Suất chiếu đã bắt đầu. Vui lòng chọn suất khác.");
        }

        Room room = findActiveRoom(showtime.getRoom());
        if (availableSeats(showtime.getId(), room) <= 0) {
            throw new IllegalArgumentException("Suất chiếu hiện đã hết chỗ hoặc chưa có sơ đồ ghế.");
        }

        return new BookingSelection(showtime.getId(), movieId, room.getId(), movie.getTitle(),
                room.getRoomName(), showtime.getShowDate(), showtime.getShowTime(),
                showtime.getShowTime().plusMinutes(resolveDuration(movie)), resolveFormat(room));
    }

    private BookingShowtimeView toView(Showtime showtime, Movie movie) {
        try {
            Room room = findActiveRoom(showtime.getRoom());
            return new BookingShowtimeView(showtime.getId(), showtime.getShowDate(), showtime.getShowTime(),
                    showtime.getShowTime().plusMinutes(resolveDuration(movie)), room.getRoomName(),
                    resolveFormat(room), availableSeats(showtime.getId(), room));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Room findActiveRoom(String roomName) {
        Room room = roomRepository.findFirstByRoomNameIgnoreCase(roomName)
                .orElseThrow(() -> new IllegalArgumentException("Phòng chiếu không tồn tại."));
        if (!ACTIVE_ROOM_STATUS.equalsIgnoreCase(room.getStatus())) {
            throw new IllegalArgumentException("Phòng chiếu đang tạm ngưng hoạt động.");
        }
        return room;
    }

    private int resolveDuration(Movie movie) {
        return movie.getDuration() == null || movie.getDuration() <= 0
                ? DEFAULT_DURATION_MINUTES : movie.getDuration();
    }

    private String resolveFormat(Room room) {
        return room.getRoomType() == null || room.getRoomType().isBlank() ? "2D" : room.getRoomType();
    }

    private String dayOfWeekLabel(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }

    private int availableSeats(Long showtimeId, Room room) {
        Map<String, SeatType> seatTypes = seatTypeRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(type -> normalizeType(type.getCode()), Function.identity(), (first, ignored) -> first));
        List<Seat> seats = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId());
        Map<Long, Seat> seatById = seats.stream().collect(Collectors.toMap(Seat::getId, Function.identity()));

        int totalCapacity = seats.stream()
                .filter(seat -> isSellableSeat(seat, seatTypes))
                .mapToInt(seat -> seatTypes.get(normalizeType(seat.getSeatType())).getCapacity())
                .sum();

        int occupiedCapacity = ticketRepository.findByShowtimeId(showtimeId).stream()
                .filter(ticket -> ticket.getStatus() == com.group3.cinema.entity.BookingTicket.Status.BOOKED
                        || (ticket.getHoldExpiresAt() != null && ticket.getHoldExpiresAt().isAfter(LocalDateTime.now())))
                .map(ticket -> seatById.get(ticket.getSeatId()))
                .filter(seat -> seat != null && isSellableSeat(seat, seatTypes))
                .mapToInt(seat -> seatTypes.get(normalizeType(seat.getSeatType())).getCapacity())
                .sum();

        return Math.max(0, totalCapacity - occupiedCapacity);
    }

    private boolean isSellableSeat(Seat seat, Map<String, SeatType> seatTypes) {
        String type = normalizeType(seat.getSeatType());
        if ("skip".equals(type)) {
            return false;
        }
        SeatType meta = seatTypes.get(type);
        return meta != null && meta.isActive() && meta.isSellable() && meta.getCapacity() > 0;
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "std" : type.trim().toLowerCase();
    }
}
