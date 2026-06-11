package com.swp392.cinema2026.service;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeService.java
 * Chức năng: Lớp nghiệp vụ (Service) xử lý các nghiệp vụ liên quan đến lịch chiếu (Showtime).
 *            Bao gồm: CRUD lịch chiếu, lọc tìm kiếm, tự động tính toán loại ngày
 *            (Ngày trong tuần/Cuối tuần/Ngày lễ), kiểm tra xung đột phòng chiếu theo thời gian,
 *            và thêm lịch chiếu hàng loạt theo dải ngày.
 * Người viết: Group 03 - SWP391
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-12
 */

import com.swp392.cinema2026.model.Showtime;
import com.swp392.cinema2026.model.Movie;
import com.swp392.cinema2026.repository.ShowtimeRepository;
import com.swp392.cinema2026.repository.MovieRepository;
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

@Service
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;

    @Autowired
    public ShowtimeService(ShowtimeRepository showtimeRepository, MovieRepository movieRepository) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
    }

    // Lấy danh sách toàn bộ lịch chiếu
    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAll();
    }

    // Lấy chi tiết lịch chiếu theo ID
    public Optional<Showtime> getShowtimeById(Long id) {
        return showtimeRepository.findById(id);
    }

    // Tìm kiếm lịch chiếu kết hợp nhiều điều kiện lọc và ngày chiếu
    public List<Showtime> searchShowtimes(Long movieId, String dayType, LocalDate startDate, LocalDate endDate) {
        return showtimeRepository.searchShowtimes(movieId, dayType, startDate, endDate);
    }

    /**
     * Kiểm tra xung đột lịch chiếu trong phòng.
     * Quy tắc chiếm dụng phòng: Giờ bắt đầu đến Giờ bắt đầu + 10 phút trailer
     * + thời lượng phim + 20 phút dọn dẹp vệ sinh.
     *
     * @param newShowtime Lịch chiếu mới đề xuất
     * @param excludeId   ID của lịch chiếu cần loại trừ (dùng khi cập nhật, null khi thêm mới)
     */
    private void checkRoomConflict(Showtime newShowtime, Long excludeId) {
        LocalDate date = newShowtime.getShowDate();
        LocalTime time = newShowtime.getShowTime();
        String room = newShowtime.getRoom();

        if (date == null || time == null || room == null || newShowtime.getMovie() == null) {
            return;
        }

        // Lấy thời lượng phim (duration) từ database để đảm bảo thông tin chính xác
        int newDuration = 120; // Giá trị mặc định nếu phim không có duration
        Movie movieInfo = movieRepository.findById(newShowtime.getMovie().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ phim tương ứng trong hệ thống!"));

        if (movieInfo.getDuration() != null && movieInfo.getDuration() > 0) {
            newDuration = movieInfo.getDuration();
        }

        // Gán lại thông tin phim đầy đủ để dùng tiêu đề hiển thị
        newShowtime.setMovie(movieInfo);

        // Tính toán khoảng thời gian chiếm dụng phòng của lịch chiếu mới
        LocalTime newStart = time;
        LocalTime newEnd = newStart.plusMinutes(10 + newDuration + 20);

        // Lấy tất cả lịch chiếu trong ngày của phòng chiếu này
        List<Showtime> existingShowtimes = showtimeRepository.findByRoomIgnoreCaseAndShowDate(room, date);

        for (Showtime exist : existingShowtimes) {
            // Loại trừ chính nó khi cập nhật
            if (excludeId != null && exist.getId().equals(excludeId)) {
                continue;
            }

            int existDuration = 120;
            if (exist.getMovie() != null && exist.getMovie().getDuration() != null) {
                existDuration = exist.getMovie().getDuration();
            }

            LocalTime existStart = exist.getShowTime();
            LocalTime existEnd = existStart.plusMinutes(10 + existDuration + 20);

            // Kiểm tra hai khoảng thời gian có giao nhau không
            // Giao nhau khi: newStart < existEnd && existStart < newEnd
            if (newStart.isBefore(existEnd) && existStart.isBefore(newEnd)) {
                throw new IllegalArgumentException(
                    String.format(
                        "Xung đột lịch chiếu! Phòng '%s' đã có lịch chiếu phim '%s' từ %s đến %s " +
                        "(đã tính 10 phút trailer và 20 phút dọn dẹp vệ sinh). Vui lòng chọn giờ khác hoặc phòng khác.",
                        room,
                        exist.getMovie() != null ? exist.getMovie().getTitle() : "?",
                        existStart.toString().substring(0, 5),
                        existEnd.toString().substring(0, 5)
                    )
                );
            }
        }
    }

    /**
     * Kiểm tra không cho phép chọn ngày/giờ chiếu trong quá khứ.
     * Chỉ cho phép từ thời điểm hiện tại và tương lai trở đi.
     */
    private void validateDateTimeNotPast(LocalDate date, LocalTime time) {
        if (date == null || time == null) return;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // Kiểm tra nếu ngày chiếu nằm trước ngày hiện tại
        if (date.isBefore(today)) {
            throw new IllegalArgumentException(
                String.format("Không thể xếp lịch chiếu cho ngày trong quá khứ! Ngày chiếu: %s (Ngày hiện tại: %s).",
                        date, today)
            );
        }

        // Nếu ngày chiếu là ngày hôm nay, kiểm tra giờ chiếu không được là giờ đã qua
        if (date.isEqual(today) && time.isBefore(now)) {
            throw new IllegalArgumentException(
                String.format("Không thể xếp lịch chiếu cho giờ chiếu đã qua hôm nay! Giờ chiếu: %s (Giờ hiện tại: %s).",
                        time.toString().substring(0, 5), now.toString().substring(0, 5))
            );
        }
    }

    // Lưu lịch chiếu mới, tự động phát hiện loại ngày chiếu
    @Transactional
    public Showtime saveShowtime(Showtime showtime) {
        // Chặn xếp lịch chiếu trong quá khứ
        validateDateTimeNotPast(showtime.getShowDate(), showtime.getShowTime());

        // Kiểm tra xung đột phòng chiếu theo thời gian trước khi lưu
        checkRoomConflict(showtime, null);

        if (showtime.getShowDate() != null) {
            showtime.setDayType(determineDayType(showtime.getShowDate()));
        }
        return showtimeRepository.save(showtime);
    }

    // Cập nhật thông tin lịch chiếu hiện có
    @Transactional
    public Showtime updateShowtime(Long id, Showtime updatedShowtime) {
        // Chặn cập nhật lịch chiếu sang ngày/giờ trong quá khứ
        validateDateTimeNotPast(updatedShowtime.getShowDate(), updatedShowtime.getShowTime());

        return showtimeRepository.findById(id).map(showtime -> {
            showtime.setMovie(updatedShowtime.getMovie());

            // Xử lý đổi ngày chiếu và tính toán lại loại ngày
            if (updatedShowtime.getShowDate() != null) {
                showtime.setShowDate(updatedShowtime.getShowDate());
                showtime.setDayType(determineDayType(updatedShowtime.getShowDate()));
            }

            showtime.setShowTime(updatedShowtime.getShowTime());
            showtime.setRoom(updatedShowtime.getRoom());

            // Kiểm tra xung đột lịch phòng chiếu mới sau khi gán thông tin (loại trừ chính nó)
            checkRoomConflict(showtime, id);

            return showtimeRepository.save(showtime);
        }).orElseThrow(() -> new RuntimeException("Showtime not found with id " + id));
    }

    /**
     * Thêm lịch chiếu hàng loạt theo khoảng ngày (từ ngày này đến ngày này).
     * Hỗ trợ tự động tính toán sinh nhiều suất chiếu liên tục trong ngày dựa theo thời lượng phim.
     * Thực hiện kiểm tra xung đột thời gian phòng cho từng suất chiếu và lưu đồng thời.
     */
    @Transactional
    public List<Showtime> saveShowtimeBatch(Long movieId, LocalDate startDate, LocalDate endDate,
                                            LocalTime showTime, String room, Integer slotCount) {
        if (startDate == null || endDate == null || showTime == null || room == null || movieId == null) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ tất cả các trường thông tin lịch chiếu!");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu!");
        }

        // Chặn ngày bắt đầu trong quá khứ
        LocalDate today = LocalDate.now();
        if (startDate.isBefore(today)) {
            throw new IllegalArgumentException("Không thể xếp lịch chiếu từ ngày trong quá khứ!");
        }

        int count = slotCount != null ? slotCount : 1;
        if (count < 1 || count > 15) {
            throw new IllegalArgumentException("Số suất chiếu trong ngày phải nằm trong khoảng từ 1 đến 15!");
        }

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bộ phim tương ứng trong hệ thống!"));

        int duration = movie.getDuration() != null && movie.getDuration() > 0 ? movie.getDuration() : 120;

        List<Showtime> savedShowtimes = new ArrayList<>();

        // Chạy vòng lặp qua từng ngày từ startDate đến endDate
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            LocalTime currentSlotTime = showTime;

            for (int i = 0; i < count; i++) {
                // Kiểm tra giờ chiếu của suất này không phải quá khứ (nếu là hôm nay)
                validateDateTimeNotPast(current, currentSlotTime);

                Showtime showtime = new Showtime();
                showtime.setMovie(movie);
                showtime.setShowDate(current);
                showtime.setShowTime(currentSlotTime);
                showtime.setRoom(room);
                showtime.setDayType(determineDayType(current));

                // Kiểm tra xung đột lịch chiếu trong ngày của phòng
                checkRoomConflict(showtime, null);

                // Lưu lịch chiếu vào cơ sở dữ liệu
                savedShowtimes.add(showtimeRepository.save(showtime));

                // Tính toán giờ chiếu cho suất tiếp theo:
                // nextStart = currentSlotTime + 10 phút trailer + duration + 20 phút dọn dẹp
                LocalTime nextStart = currentSlotTime.plusMinutes(10 + duration + 20);

                // Làm tròn lên bội số của 5 phút gần nhất
                int minute = nextStart.getMinute();
                int rem = minute % 5;
                if (rem > 0) {
                    nextStart = nextStart.plusMinutes(5 - rem);
                }

                // Nếu giờ suất tiếp theo bị tràn qua ngày mới, dừng sinh suất trong ngày
                if (nextStart.isBefore(currentSlotTime)) {
                    break;
                }

                currentSlotTime = nextStart;
            }

            // Tăng ngày tiếp theo
            current = current.plusDays(1);
        }

        return savedShowtimes;
    }

    // Xóa lịch chiếu khỏi cơ sở dữ liệu
    public void deleteShowtime(Long id) {
        showtimeRepository.deleteById(id);
    }

    // Thống kê số lượng lịch chiếu theo tổng số và theo phân loại ngày
    public Map<String, Long> getShowtimeStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total",   showtimeRepository.count());
        stats.put("weekday", showtimeRepository.countByDayType("Trong tuần"));
        stats.put("weekend", showtimeRepository.countByDayType("Cuối tuần"));
        stats.put("holiday", showtimeRepository.countByDayType("Ngày lễ"));
        return stats;
    }

    // Hàm tiện ích xác định loại ngày tự động dựa trên ngày được chọn (dương lịch Việt Nam)
    public String determineDayType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        // Kiểm tra các ngày lễ lớn cố định theo Dương lịch tại Việt Nam
        if ((month == 1 && day == 1) ||    // Tết Dương Lịch
            (month == 4 && day == 30) ||   // Ngày Giải Phóng Miền Nam 30/4
            (month == 5 && day == 1) ||    // Ngày Quốc Tế Lao Động 1/5
            (month == 9 && day == 2)) {    // Ngày Quốc Khánh 2/9
            return "Ngày lễ";
        }

        // Kiểm tra nếu ngày thuộc Thứ 7 hoặc Chủ Nhật (Cuối tuần)
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "Cuối tuần";
        }

        // Ngày thường (Trong tuần: Thứ 2 đến Thứ 6)
        return "Trong tuần";
    }
}
