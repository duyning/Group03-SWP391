package com.group3.cinema.service.api;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeService.java
 * Chức năng: Lớp nghiệp vụ (Service) xử lý các nghiệp vụ liên quan đến lịch chiếu (Showtime).
 *            Bao gồm: CRUD lịch chiếu, lọc tìm kiếm, tự động tính toán loại ngày
 *            (Ngày trong tuần/Cuối tuần/Ngày lễ), kiểm tra xung đột phòng chiếu theo thời gian,
 *            và thêm lịch chiếu hàng loạt theo dải ngày.
 * Người viết: Group 03 - SWP391
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-23
 * Chi tiết thay đổi:
 * - [SỬA - TrienLX - 2026-06-23] Viết lại overrideSingleDay() để CẬP NHẬT bản ghi gốc thay vì
 *   tạo bản ghi mới, giải quyết vấn đề trùng lặp và tổng suất chiếu bị sai.
 */

import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Đánh dấu lớp này là một Service xử lý nghiệp vụ quản lý lịch chiếu
@Service
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final com.group3.cinema.service.TicketService ticketService;
    private final com.group3.cinema.repository.TicketRepository ticketRepository;

    @Autowired
    public ShowtimeService(ShowtimeRepository showtimeRepository, 
                            MovieRepository movieRepository,
                            com.group3.cinema.service.TicketService ticketService,
                            com.group3.cinema.repository.TicketRepository ticketRepository) {
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

    private void checkRoomConflict(Showtime newShowtime, Long excludeId) {
        LocalDate date = newShowtime.getShowDate();
        LocalTime time = newShowtime.getShowTime();
        String room = newShowtime.getRoom();

        if (date == null || time == null || room == null || newShowtime.getMovie() == null) {
            return;
        }

        int newDuration = 120;
        Movie movieInfo = movieRepository.findById(newShowtime.getMovie().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ phim tương ứng trong hệ thống!"));

        if (movieInfo.getDuration() != null && movieInfo.getDuration() > 0) {
            newDuration = movieInfo.getDuration();
        }

        newShowtime.setMovie(movieInfo);

        LocalTime newStart = time;
        LocalTime newEnd = newStart.plusMinutes(10 + newDuration + 20);

        List<Showtime> existingShowtimes = showtimeRepository.findByRoomIgnoreCaseAndShowDate(room, date);

        for (Showtime exist : existingShowtimes) {
            if (excludeId != null && exist.getId().equals(excludeId)) {
                continue;
            }

            int existDuration = 120;
            if (exist.getMovie() != null && exist.getMovie().getDuration() != null) {
                existDuration = exist.getMovie().getDuration();
            }

            LocalTime existStart = exist.getShowTime();
            LocalTime existEnd = existStart.plusMinutes(10 + existDuration + 20);

            if (newStart.isBefore(existEnd) && existStart.isBefore(newEnd)) {
                throw new IllegalArgumentException(
                    String.format(
                        "Xung đột lịch chiếu! Phòng '%s' đã có lịch chiếu phim '%s' từ %s đến %s. Vui lòng chọn giờ khác hoặc phòng khác.",
                        room,
                        exist.getMovie() != null ? exist.getMovie().getTitle() : "?",
                        existStart.toString().substring(0, 5),
                        existEnd.toString().substring(0, 5)
                    )
                );
            }
        }
    }

    private void validateDateTimeNotPast(LocalDate date, LocalTime time) {
        if (date == null || time == null) return;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (date.isBefore(today)) {
            throw new IllegalArgumentException("Không thể xếp lịch chiếu cho ngày trong quá khứ!");
        }

        if (date.isEqual(today) && time.isBefore(now)) {
            throw new IllegalArgumentException("Không thể xếp lịch chiếu cho giờ chiếu đã qua hôm nay!");
        }
    }

    @Transactional
    public Showtime saveShowtime(Showtime showtime) {
        validateDateTimeNotPast(showtime.getShowDate(), showtime.getShowTime());
        checkRoomConflict(showtime, null);

        if (showtime.getShowDate() != null) {
            showtime.setDayType(determineDayType(showtime.getShowDate()));
        }
        Showtime saved = showtimeRepository.save(showtime);
        ticketService.generateTicketsForShowtime(saved);
        return saved;
    }

    @Transactional
    public Showtime updateShowtime(Long id, Showtime updatedShowtime) {
        validateDateTimeNotPast(updatedShowtime.getShowDate(), updatedShowtime.getShowTime());

        return showtimeRepository.findById(id).map(showtime -> {
            showtime.setMovie(updatedShowtime.getMovie());
            if (updatedShowtime.getShowDate() != null) {
                showtime.setShowDate(updatedShowtime.getShowDate());
                showtime.setDayType(determineDayType(updatedShowtime.getShowDate()));
            }

            showtime.setShowTime(updatedShowtime.getShowTime());
            showtime.setRoom(updatedShowtime.getRoom());

            checkRoomConflict(showtime, id);

            Showtime saved = showtimeRepository.save(showtime);
            ticketService.generateTicketsForShowtime(saved);
            return saved;
        }).orElseThrow(() -> new RuntimeException("Showtime not found with id " + id));
    }

    @Transactional
    public List<Showtime> updateShowtimeBatch(Long id, com.group3.cinema.controller.api.ShowtimeController.ShowtimeRequest req) {
        // 1. Xóa các lịch chiếu cũ trong nhóm để tránh xung đột với chính nó khi lưu dải ngày mới
        if (req.getGroupIds() != null && !req.getGroupIds().isEmpty()) {
            for (Long oldId : req.getGroupIds()) {
                if (showtimeRepository.existsById(oldId)) {
                    showtimeRepository.deleteById(oldId);
                }
            }
        } else {
            if (showtimeRepository.existsById(id)) {
                showtimeRepository.deleteById(id);
            }
        }

        // Đẩy thay đổi xóa xuống database ngay lập tức để checkRoomConflict ở bước sau không bị nhận diện xung đột
        showtimeRepository.flush();

        // 2. Tạo dải lịch chiếu mới
        return saveShowtimeBatch(
                req.getMovieId(),
                req.getStartDate(),
                req.getEndDate() != null ? req.getEndDate() : req.getStartDate(),
                req.getShowTime(),
                req.getRoom(),
                1 // Khóa slotCount = 1 khi chỉnh sửa dải ngày
        );
    }

    @Transactional
    public List<Showtime> saveShowtimeBatch(Long movieId, LocalDate startDate, LocalDate endDate,
                                            LocalTime showTime, String room, Integer slotCount) {
        if (startDate == null || endDate == null || showTime == null || room == null || movieId == null) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ thông tin!");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu!");
        }

        int count = slotCount != null ? slotCount : 1;
        Movie movie = movieRepository.findById(movieId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ phim!"));

        int duration = movie.getDuration() != null && movie.getDuration() > 0 ? movie.getDuration() : 120;
        List<Showtime> savedShowtimes = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            LocalTime currentSlotTime = showTime;
            LocalDate slotDate = current;
            for (int i = 0; i < count; i++) {
                validateDateTimeNotPast(slotDate, currentSlotTime);

                Showtime showtime = new Showtime();
                showtime.setMovie(movie);
                showtime.setShowDate(slotDate);
                showtime.setShowTime(currentSlotTime);
                showtime.setRoom(room);
                showtime.setDayType(determineDayType(slotDate));

                checkRoomConflict(showtime, null);
                Showtime saved = showtimeRepository.save(showtime);
                ticketService.generateTicketsForShowtime(saved);
                savedShowtimes.add(saved);

                LocalTime nextStart = currentSlotTime.plusMinutes(10 + duration + 20);
                int minute = nextStart.getMinute();
                int rem = minute % 5;
                if (rem > 0) nextStart = nextStart.plusMinutes(5 - rem);

                if (nextStart.isBefore(currentSlotTime)) {
                    slotDate = slotDate.plusDays(1);
                }
                currentSlotTime = nextStart;
            }
            current = current.plusDays(1);
        }
        return savedShowtimes;
    }

    /**
     * [SỬA - TrienLX - 2026-06-23]
     * Điều chỉnh giờ chiếu của 1 ngày cụ thể trong một nhóm lịch chiếu.
     *
     * Logic mới (FIX BUG TẠO TRÚNG):
     *   - Nếu có originalShowtimeId: tìm bản ghi theo ID đó và CẬP NHẬT tại chỗ.
     *   - Nếu không có: tìm bản ghi gốc theo movieId + targetDate (lấy suất đầu tiên trong ngày)
     *     và CẬP NHẬT tại chỗ.
     *   - Đánh dấu isOverride = true và note = "Dã điều chỉnh" cho bản ghi đó.
     *   - KHÔNG tạo bản ghi mới → tổng suất chiếu luôn đúng.
     *
     * @param originalShowtimeId ID của bản ghi gốc cần điều chỉnh (tùy chọn, những ảnh hưởng đến độ chính xác)
     * @param movieId ID phim
     * @param targetDate Ngày chiếu cần điều chỉnh
     * @param newShowTime Giờ chiếu mới
     * @param room Phòng chiếu mới
     */
    @Transactional
    public Showtime overrideSingleDay(Long originalShowtimeId, Long movieId, LocalDate targetDate,
                                      LocalTime newShowTime, String room) {
        if (targetDate == null || newShowTime == null || room == null || movieId == null) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ thông tin để điều chỉnh suất chiếu!");
        }

        // Kiểm tra ngày không được là quá khứ
        validateDateTimeNotPast(targetDate, newShowTime);

        Showtime target;

        if (originalShowtimeId != null) {
            // Ưu tiên: tìm chính xác bảng ID của bản ghi cần điều chỉnh
            target = showtimeRepository.findById(originalShowtimeId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy suất chiếu gốc có ID = " + originalShowtimeId));
        } else {
            // Fallback: tìm theo movieId + ngày, lấy suất đầu tiên trong ngày
            List<Showtime> candidates = showtimeRepository.findByMovieIdAndShowDate(
                    movieId.intValue(), targetDate);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "Không tìm thấy suất chiếu nào của phim này trong ngày " + targetDate);
            }
            // Lấy suất chưa bị override trước (nếu có), hoặc suất đầu tiên
            target = candidates.stream()
                    .filter(s -> !s.isOverride())
                    .findFirst()
                    .orElse(candidates.get(0));
        }

        // Cập nhật thông tin: giờ chiếu mới, phòng mới, đánh dấu override
        target.setShowTime(newShowTime);
        target.setRoom(room);
        target.setDayType(determineDayType(targetDate)); // re-calculate dựa ngày gốc (giữ nguyên)
        target.setOverride(true);                        // Đánh dấu đã điều chỉnh riêng
        target.setNote("Đã điều chỉnh");              // Hiển thị badge trên UI

        // Kiểm tra xung đột phòng chiếu (loại trừ chính bản ghi này khỏi kiểm tra)
        checkRoomConflict(target, target.getId());

        Showtime saved = showtimeRepository.save(target);
        ticketService.generateTicketsForShowtime(saved);
        return saved;
    }

    @Transactional
    public void deleteShowtime(Long id) {
        boolean hasSold = ticketRepository.existsByShowtimeIdAndStatus(id, "Đã bán");
        if (hasSold) {
            throw new IllegalStateException("Không thể xóa suất chiếu này vì đã có vé bán ra!");
        }
        ticketRepository.deleteUnsoldTicketsByShowtimeId(id);
        showtimeRepository.deleteById(id);
    }

    public Map<String, Long> getShowtimeStats() {
        Map<String, Long> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        // Theo trạng thái thời gian
        stats.put("total",    showtimeRepository.count());
        stats.put("active",   showtimeRepository.countByShowDate(today));
        stats.put("upcoming", showtimeRepository.countByShowDateGreaterThan(today));
        stats.put("ended",    showtimeRepository.countByShowDateLessThan(today));
        // Theo loại ngày chiếu
        stats.put("weekday",  showtimeRepository.countByDayType("Trong tuần"));
        stats.put("weekend",  showtimeRepository.countByDayType("Cuối tuần"));
        stats.put("holiday",  showtimeRepository.countByDayType("Ngày lễ"));
        return stats;
    }

    public String determineDayType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if ((month == 1 && day == 1) || (month == 4 && day == 30) ||
            (month == 5 && day == 1) || (month == 9 && day == 2)) {
            return "Ngày lễ";
        }

        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "Cuối tuần";
        }

        return "Trong tuần";
    }

//    // Táº¡o 40 vÃ© cho sÆ¡ Ä‘á»“ gháº¿ cá»§a lá»‹ch chiáº¿u (A1-A8, B1-B8, C1-C8 lÃ  Gháº¿ thÆ°á»ng; D1-D8, E1-E8 lÃ  Gháº¿ VIP)
//    private void generateTicketsForShowtime(Showtime showtime) {
//        String[] rows = {"A", "B", "C", "D", "E"};
//        int seatsPerRow = 8;
//        String dayType = showtime.getDayType();
//
//        for (String row : rows) {
//            // HÃ ng D vÃ  E lÃ  gháº¿ VIP, cÃ²n láº¡i lÃ  gháº¿ thÆ°á»ng
//            String seatType = (row.equals("D") || row.equals("E")) ? "VIP" : "ThÆ°á»ng";
//            double price = calculateTicketPrice(dayType, seatType);
//
//
//        }
    }

//    // TÃ­nh toÃ¡n giÃ¡ vÃ© thá»±c táº¿ dá»±a theo ma tráº­n phÃ¢n loáº¡i ngÃ y vÃ  loáº¡i gháº¿
//    private double calculateTicketPrice(String dayType, String seatType) {
//        if ("Trong tuáº§n".equals(dayType)) {
//            return "VIP".equals(seatType) ? 100000.0 : 80000.0;
//        } else if ("Cuá»‘i tuáº§n".equals(dayType)) {
//            return "VIP".equals(seatType) ? 120000.0 : 100000.0;
//        } else if ("NgÃ y lá»…".equals(dayType)) {
//            return "VIP".equals(seatType) ? 140000.0 : 120000.0;
//        }
//        return "VIP".equals(seatType) ? 100000.0 : 80000.0;
//    }
