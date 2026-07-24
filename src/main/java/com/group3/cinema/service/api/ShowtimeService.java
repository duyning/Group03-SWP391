/**
 * Service xử lý logic nghiệp vụ quản lý Suất chiếu phim (`ShowtimeService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ShowtimeApiController`, `CustomerBookingService`, `PublicContentInitializer`.
 * - Gọi tới các Repository:
 *   + `ShowtimeRepository`: Truy vấn và lưu bản ghi suất chiếu (`save`, `searchShowtimes`, `findByRoomIgnoreCaseAndShowDate`).
 *   + `MovieRepository`: Lấy thông tin thời lượng phim (`resolveDuration`).
 *   + `TicketRepository`: Kiểm tra ràng buộc và dọn dẹp vé chưa bán khi chỉnh sửa/xóa suất chiếu (`hasBookedTicketsForShowtime`, `deleteAllByShowtimeId`).
 *   + `BookingRepository`: Kiểm tra các đơn đặt vé active đang được xử lý (`hasActiveBookingsForShowtime`).
 * 
 * Logic nghiệp vụ chính:
 * - Tự động tính toán loại ngày (`determineDayType`: "Trong tuần", "Cuối tuần", "Ngày lễ").
 * - Kiểm tra trùng lặp lịch chiếu theo từng phòng chiếu (`validateRoomTimeOverlap`), tự động cộng thời gian giãn cách dọn dẹp phòng (30 phút).
 * - Hỗ trợ xếp lịch theo lô hàng loạt cho nhiều ngày và nhiều phòng (`saveShowtimeBatch`).
 * 
 * Khởi tạo bởi: Group 03 - SWP391, NinhDD - HE186113, TrienLX
 */
package com.group3.cinema.service.api;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final TicketRepository ticketRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.group3.cinema.repository.BookingTicketRepository bookingTicketRepository;

    public ShowtimeService(ShowtimeRepository showtimeRepository,
                           MovieRepository movieRepository,
                           TicketRepository ticketRepository) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.ticketRepository = ticketRepository;
    }

    /** Lấy danh sách tất cả suất chiếu active. */
    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAll();
    }

    /** Tìm chi tiết suất chiếu theo ID. */
    public Optional<Showtime> getShowtimeById(Long id) {
        return showtimeRepository.findById(id);
    }

    /** Tìm kiếm danh sách suất chiếu đa điều kiện (theo phim, loại ngày, từ ngày đến ngày). */
    public List<Showtime> searchShowtimes(Integer movieId, String dayType, LocalDate startDate, LocalDate endDate) {
        return showtimeRepository.searchShowtimes(movieId, dayType, startDate, endDate);
    }

    /** Lưu đơn lẻ một suất chiếu phim mới. */
    @Transactional
    public Showtime saveShowtime(Showtime showtime) {
        prepareAndValidateShowtime(showtime, null, false);
        showtime.setDayType(determineDayType(showtime.getShowDate()));
        showtime.setActive(true);
        return showtimeRepository.save(showtime);
    }

    /** Cập nhật thông tin một suất chiếu đơn lẻ đã có. */
    @Transactional
    public Showtime updateShowtime(Long id, Showtime updatedShowtime) {
        return showtimeRepository.findById(id).map(showtime -> {
            validateShowtimeEditable(id);
            prepareAndValidateShowtime(updatedShowtime, id, true);
            showtime.setMovie(updatedShowtime.getMovie());
            showtime.setShowDate(updatedShowtime.getShowDate());
            showtime.setShowTime(updatedShowtime.getShowTime());
            showtime.setRoom(updatedShowtime.getRoom());
            showtime.setDayType(determineDayType(updatedShowtime.getShowDate()));
            return showtimeRepository.save(showtime);
        }).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));
    }

    /** Cập nhật một nhóm hàng loạt suất chiếu cũ bằng nhóm suất chiếu mới. */
    @Transactional
    public List<Showtime> updateShowtimeBatch(Long id, com.group3.cinema.controller.api.ShowtimeController.ShowtimeRequest req) {
        if (req.getGroupIds() != null && !req.getGroupIds().isEmpty()) {
            for (Long oldId : req.getGroupIds()) {
                validateShowtimeEditable(oldId);
            }
            for (Long oldId : req.getGroupIds()) {
                deleteShowtimeInternal(oldId);
            }
        } else {
            validateShowtimeEditable(id);
            deleteShowtimeInternal(id);
        }

        showtimeRepository.flush();

        if (req.getShowTimes() != null && !req.getShowTimes().isEmpty()) {
            return saveShowtimeBatch(
                    req.getMovieId(),
                    req.getStartDate(),
                    req.getEndDate() != null ? req.getEndDate() : req.getStartDate(),
                    req.getShowTimes(),
                    req.getRoom(),
                    true
            );
        }

        return saveShowtimeBatch(
                req.getMovieId(),
                req.getStartDate(),
                req.getEndDate() != null ? req.getEndDate() : req.getStartDate(),
                req.getShowTime(),
                req.getRoom(),
                1,
                true
        );
    }

    /** Xếp lịch chiếu hàng loạt theo danh sách các khung giờ chỉ định. */
    @Transactional
    public List<Showtime> saveShowtimeBatch(Long movieId,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            List<LocalTime> showTimes,
                                            String room,
                                            boolean skipPastValidation) {
        if (movieId == null || startDate == null || endDate == null || showTimes == null || showTimes.isEmpty()
                || room == null || room.isBlank()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ thông tin lịch chiếu.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        Movie movie = movieRepository.findById(movieId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Phim được chọn không tồn tại."));
        List<Showtime> savedShowtimes = new ArrayList<>();

        for (String roomName : splitRooms(room)) {
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                for (LocalTime showTime : showTimes) {
                    if (!skipPastValidation) {
                        validateDateTimeNotPast(current, showTime);
                    }

                    Showtime showtime = new Showtime();
                    showtime.setMovie(movie);
                    showtime.setShowDate(current);
                    showtime.setShowTime(showTime);
                    showtime.setRoom(roomName);
                    showtime.setDayType(determineDayType(current));
                    showtime.setActive(true);

                    validateRoomTimeOverlap(showtime, null);
                    savedShowtimes.add(showtimeRepository.save(showtime));
                }
                current = current.plusDays(1);
            }
        }

        return savedShowtimes;
    }

    /** Xếp lịch chiếu hàng loạt liên tiếp nhiều slot từ một mốc giờ bắt đầu. */
    @Transactional
    public List<Showtime> saveShowtimeBatch(Long movieId,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            LocalTime showTime,
                                            String room,
                                            Integer slotCount) {
        return saveShowtimeBatch(movieId, startDate, endDate, showTime, room, slotCount, false);
    }

    /** Xếp lịch chiếu hàng loạt liên tiếp với tùy chọn bỏ qua kiểm tra thời gian quá khứ khi edit. */
    @Transactional
    public List<Showtime> saveShowtimeBatch(Long movieId,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            LocalTime showTime,
                                            String room,
                                            Integer slotCount,
                                            boolean skipPastValidation) {
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

        for (String roomName : splitRooms(room)) {
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                LocalTime currentSlotTime = showTime;
                LocalDate slotDate = current;

                for (int i = 0; i < count; i++) {
                    if (!skipPastValidation) {
                        validateDateTimeNotPast(slotDate, currentSlotTime);
                    }

                    Showtime showtime = new Showtime();
                    showtime.setMovie(movie);
                    showtime.setShowDate(slotDate);
                    showtime.setShowTime(currentSlotTime);
                    showtime.setRoom(roomName);
                    showtime.setDayType(determineDayType(slotDate));
                    showtime.setActive(true);

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
        }

        return savedShowtimes;
    }

    /** Điều chỉnh thông tin riêng cho một ngày chiếu cụ thể (Ghi đè - Override). */
    @Transactional
    public Showtime overrideSingleDay(Long originalShowtimeId,
                                      Long movieId,
                                      LocalDate targetDate,
                                      LocalTime newShowTime,
                                      String room) {
        if (targetDate == null || newShowTime == null || room == null || room.isBlank() || movieId == null) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ thông tin để điều chỉnh suất chiếu.");
        }

        validateDateTimeNotPast(targetDate, newShowTime);
        Showtime target;
        if (originalShowtimeId != null) {
            target = showtimeRepository.findById(originalShowtimeId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu gốc."));
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
        return showtimeRepository.save(target);
    }

    /** Xóa suất chiếu theo ID. */
    @Transactional
    public boolean deleteShowtime(Long id) {
        return deleteShowtimeInternal(id);
    }

    /** Thực hiện dọn dẹp bản ghi vé chưa bán và xóa bản ghi suất chiếu. */
    private boolean deleteShowtimeInternal(Long id) {
        if (id == null || !showtimeRepository.existsById(id)) {
            return false;
        }
        validateShowtimeEditable(id);

        bookingTicketRepository.deleteByShowtimeId(id);
        ticketRepository.deleteAllByShowtimeId(id);
        showtimeRepository.deleteById(id);
        return false;
    }

    /** Kiểm tra suất chiếu có thể sửa/xóa hay không (chặn khi đã hết giờ, đã có vé bán hoặc có khách giữ chỗ). */
    private void validateShowtimeEditable(Long id) {
        if (id == null) return;
        Showtime showtime = showtimeRepository.findById(id).orElse(null);
        if (showtime == null) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime showDateTime = LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime());
        if (showDateTime.isBefore(now)) {
            throw new IllegalArgumentException("Suất chiếu đã kết thúc, không thể chỉnh sửa hoặc xóa.");
        }

        if (ticketRepository.hasBookedTicketsForShowtime(id)) {
            throw new IllegalArgumentException("Không thể chỉnh sửa hoặc xóa suất chiếu này vì đã có vé được đặt.");
        }
        if (bookingRepository.hasActiveBookingsForShowtime(id, now) || bookingTicketRepository.hasActiveHoldingsOrBookingsForShowtime(id, now)) {
            throw new IllegalArgumentException("Không thể chỉnh sửa hoặc xóa suất chiếu này vì đang có khách hàng giữ ghế/thực hiện mua vé.");
        }
    }

    /** Kiểm tra tính hợp lệ tổng thể của thông tin suất chiếu trước khi ghi CSDL. */
    private void prepareAndValidateShowtime(Showtime showtime, Long editingId, boolean allowPastWhenEditing) {
        if (showtime == null) {
            throw new IllegalArgumentException("Dữ liệu lịch chiếu không hợp lệ.");
        }
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
        if (!allowPastWhenEditing) {
            validateDateTimeNotPast(showtime.getShowDate(), showtime.getShowTime());
        }
        validateRoomTimeOverlap(showtime, editingId);
    }

    /** Kiểm tra không cho phép tạo/sửa lịch chiếu ở thời điểm quá khứ. */
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

    /** Kiểm tra xem khung giờ dự định chiếu có bị trùng đè lên suất chiếu khác trong cùng phòng hay không. */
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

    /** Tách chuỗi danh sách phòng chiếu bằng dấu phẩy. */
    private List<String> splitRooms(String room) {
        List<String> rooms = new ArrayList<>();
        for (String value : room.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                rooms.add(trimmed);
            }
        }
        if (rooms.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn phòng chiếu.");
        }
        return rooms;
    }

    /** Quy đổi LocalTime thành tổng số phút trong ngày. */
    private int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /** Lấy thời lượng phim (phút), mặc định 120 phút nếu không có dữ liệu. */
    private int resolveDuration(Movie movie) {
        if (movie == null || movie.getDuration() == null || movie.getDuration() <= 0) {
            return DEFAULT_MOVIE_DURATION_MINUTES;
        }
        return movie.getDuration();
    }

    /** Lấy số liệu thống kê suất chiếu cho Dashboard quản trị. */
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

    /** Xác định loại ngày (Trong tuần, Cuối tuần, Ngày lễ) dựa theo ngày chiếu. */
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

