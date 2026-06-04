package com.swp392.cinema2026.service;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeService.java
 * Chức năng: Lớp nghiệp vụ (Service) xử lý các nghiệp vụ liên quan đến lịch chiếu (Showtime).
 *            Bao gồm: CRUD lịch chiếu, lọc tìm kiếm, tự động tính toán loại ngày (Ngày trong tuần/Cuối tuần/Ngày lễ)
 *            và xác định giá vé cơ sở tương ứng cho từng lịch chiếu.
 * Người viết: Group 03 - SWP391
 * Ngày tạo: 2026-06-04
 */

import com.swp392.cinema2026.model.Showtime;
import com.swp392.cinema2026.repository.ShowtimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Đánh dấu lớp này là một Service xử lý nghiệp vụ quản lý lịch chiếu
@Service
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;

    @Autowired
    public ShowtimeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
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

    // Lưu lịch chiếu mới, tự động phát hiện loại ngày chiếu và tạo sơ đồ 40 ghế ngồi (vé)
    @Transactional
    public Showtime saveShowtime(Showtime showtime) {
        if (showtime.getShowDate() != null) {
            showtime.setDayType(determineDayType(showtime.getShowDate()));
        }
        Showtime savedShowtime = showtimeRepository.save(showtime);
        
        // Tự động sinh 40 vé ngồi trống cho suất chiếu mới này
//        generateTicketsForShowtime(savedShowtime);
        
        return savedShowtime;
    }

    // Cập nhật thông tin lịch chiếu hiện có, tự tính toán lại giá vé nếu ngày chiếu thay đổi loại ngày
    @Transactional
    public Showtime updateShowtime(Long id, Showtime updatedShowtime) {
        return showtimeRepository.findById(id).map(showtime -> {
            showtime.setMovie(updatedShowtime.getMovie());
            
            // Xử lý đổi ngày chiếu và tính toán lại loại ngày
            if (updatedShowtime.getShowDate() != null) {
                String oldDayType = showtime.getDayType();
                String newDayType = determineDayType(updatedShowtime.getShowDate());
                showtime.setShowDate(updatedShowtime.getShowDate());
                showtime.setDayType(newDayType);
            }
            
            showtime.setShowTime(updatedShowtime.getShowTime());
            showtime.setRoom(updatedShowtime.getRoom());
            
            return showtimeRepository.save(showtime);
        }).orElseThrow(() -> new RuntimeException("Showtime not found with id " + id));
    }

    // Xóa lịch chiếu khỏi cơ sở dữ liệu (tự động xóa sạch vé liên quan nhờ CascadeType.ALL)
    public void deleteShowtime(Long id) {
        showtimeRepository.deleteById(id);
    }

    // Thống kê số lượng lịch chiếu theo tổng số và theo phân loại ngày
    public Map<String, Long> getShowtimeStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", showtimeRepository.count());
        stats.put("weekday", showtimeRepository.countByDayType("Trong tuần"));
        stats.put("weekend", showtimeRepository.countByDayType("Cuối tuần"));
        stats.put("holiday", showtimeRepository.countByDayType("Ngày lễ"));
        return stats;
    }

    // Hàm tiện ích xác định loại ngày tự động dựa trên ngày được chọn (dương lịch Việt Nam)
    public String determineDayType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        // 1. Kiểm tra các ngày lễ lớn cố định theo Dương lịch tại Việt Nam
        if ((month == 1 && day == 1) ||    // Tết Dương Lịch
            (month == 4 && day == 30) ||   // Ngày Giải Phóng Miền Nam 30/4
            (month == 5 && day == 1) ||    // Ngày Quốc Tế Lao Động 1/5
            (month == 9 && day == 2)) {    // Ngày Quốc Khánh 2/9
            return "Ngày lễ";
        }

        // 2. Kiểm tra nếu ngày thuộc Thứ 7 hoặc Chủ Nhật (Cuối tuần)
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "Cuối tuần";
        }

        // 3. Ngày thường (Trong tuần: Thứ 2 đến Thứ 6)
        return "Trong tuần";
    }

//    // Tạo 40 vé cho sơ đồ ghế của lịch chiếu (A1-A8, B1-B8, C1-C8 là Ghế thường; D1-D8, E1-E8 là Ghế VIP)
//    private void generateTicketsForShowtime(Showtime showtime) {
//        String[] rows = {"A", "B", "C", "D", "E"};
//        int seatsPerRow = 8;
//        String dayType = showtime.getDayType();
//
//        for (String row : rows) {
//            // Hàng D và E là ghế VIP, còn lại là ghế thường
//            String seatType = (row.equals("D") || row.equals("E")) ? "VIP" : "Thường";
//            double price = calculateTicketPrice(dayType, seatType);
//
//
//        }
    }

//    // Tính toán giá vé thực tế dựa theo ma trận phân loại ngày và loại ghế
//    private double calculateTicketPrice(String dayType, String seatType) {
//        if ("Trong tuần".equals(dayType)) {
//            return "VIP".equals(seatType) ? 100000.0 : 80000.0;
//        } else if ("Cuối tuần".equals(dayType)) {
//            return "VIP".equals(seatType) ? 120000.0 : 100000.0;
//        } else if ("Ngày lễ".equals(dayType)) {
//            return "VIP".equals(seatType) ? 140000.0 : 120000.0;
//        }
//        return "VIP".equals(seatType) ? 100000.0 : 80000.0;
//    }

