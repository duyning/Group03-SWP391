package com.group3.cinema.controller;

import com.group3.cinema.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/admin/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public String viewRevenueReport(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        // Mặc định hiển thị bộ lọc nếu chưa chọn ngày
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        // Lấy dữ liệu chế biến từ Service
        Map<String, Object> reportData = reportService.getRevenueAnalysis(startDate, endDate);

        // Đẩy toàn bộ dữ liệu thống kê ra Model cho Thymeleaf hiển thị
        model.addAttribute("totalRevenue", reportData.get("totalRevenue"));
        model.addAttribute("paymentStats", reportData.get("paymentStats"));
        model.addAttribute("movieStats", reportData.get("movieStats"));
        model.addAttribute("comboStats", reportData.get("comboStats"));
        model.addAttribute("seatTypeStats", reportData.get("seatTypeStats"));

        // Giữ lại ngày Admin đã chọn trên thanh tìm kiếm (Filter)
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "revenue-report"; //
    }
}