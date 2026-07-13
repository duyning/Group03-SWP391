package com.group3.cinema.service;

import com.group3.cinema.repository.ReportRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final ReportRepository reportRepository;

    // Dùng constructor truyền thống để đảm bảo không lỗi nếu cậu không cài Lombok
    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public Map<String, Object> getRevenueAnalysis(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> reportData = new HashMap<>();

        // Nếu admin không chọn ngày, mặc định lấy 30 ngày gần nhất
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : LocalDateTime.now();

        // 1. Tổng doanh thu tiền mặt thu về trong khoảng thời gian chọn
        Double totalRevenue = reportRepository.sumRevenueByDateRange(start, end);
        reportData.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);

        // 2. Thống kê doanh thu theo Phương thức thanh toán (VNPAY, PAYOS...)
        List<Object[]> paymentStats = reportRepository.sumRevenueByPaymentMethod();
        reportData.put("paymentStats", paymentStats);

        // 3. Thống kê doanh thu theo Phim
        List<Object[]> movieStats = reportRepository.sumRevenueByMovie();
        reportData.put("movieStats", movieStats);

        // 4. Thống kê doanh thu theo Combo bỏng nước
        List<Object[]> comboStats = reportRepository.sumRevenueByCombo();
        reportData.put("comboStats", comboStats);

        // 5. Thống kê số lượng & doanh thu theo Loại ghế (Standard, VIP...)
        List<Object[]> seatTypeStats = reportRepository.sumRevenueBySeatType();
        reportData.put("seatTypeStats", seatTypeStats);

        return reportData;
    }
}