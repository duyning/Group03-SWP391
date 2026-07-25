package com.group3.cinema.controller;

/*
 * Created on 2026-06-09: Added admin dashboard route for manager/admin login flow.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.*;
import com.group3.cinema.repository.api.ShowtimeRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AdminDashboardController {

    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingRepository bookingRepository;
    private final AccountRepository accountRepository;
    private final BookingTicketRepository bookingTicketRepository;
    private final PaymentRepository paymentRepository;

    public AdminDashboardController(MovieRepository movieRepository,
                                    RoomRepository roomRepository,
                                    SeatRepository seatRepository,
                                    ShowtimeRepository showtimeRepository,
                                    BookingRepository bookingRepository,
                                    AccountRepository accountRepository,
                                    BookingTicketRepository bookingTicketRepository,
                                    PaymentRepository paymentRepository) {
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.bookingRepository = bookingRepository;
        this.accountRepository = accountRepository;
        this.bookingTicketRepository = bookingTicketRepository;
        this.paymentRepository = paymentRepository;
    }

    public record BookingDto(
        Long id,
        String customerName,
        String movieTitle,
        int ticketQuantity,
        BigDecimal totalPrice,
        String bookingTime,
        String status
    ) {}

    public record MovieRevenueDto(
        String title,
        double revenue,
        long ticketsSold
    ) {}

    public record PaymentMethodStatDto(
        String method,
        double revenue,
        long count
    ) {}

    /**
     * BƯỚC 1: Xử lý request GET /admin/dashboard cho giao diện Dashboard Quản trị.
     * Luồng gọi: Trình duyệt gửi GET /admin/dashboard?year=... -> AdminDashboardController.showDashboard(...)
     * Dữ liệu được tính toán và đẩy vào Spring Model -> Render View "admin_dashboard.html".
     */
    @GetMapping("/admin/dashboard")
    public String showDashboard(@RequestParam(value = "year", required = false) Integer year,
                               HttpSession session, Model model) {
        // [Lấy thông tin người dùng đang đăng nhập từ Session]
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }

        // [Xử lý thông báo lỗi từ các controller khác chuyển hướng tới nếu có]
        Object errorMessage = session.getAttribute("errorMessage");
        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage);
            session.removeAttribute("errorMessage");
        }

        // [LUỒNG XỬ LÝ 1: Xác định Năm được chọn và khoảng ngày từ 01/01 đến 31/12 của năm đó]
        // Mặc định lấy Năm hiện tại theo thời gian thực (nếu không có tham số year được gửi từ dropdown)
        int currentYear = java.time.LocalDate.now().getYear();
        int selectedYear = (year != null && year > 2000) ? year : currentYear;

        // Tạo ngày bắt đầu (01/01/selectedYear) và ngày kết thúc (31/12/selectedYear) để làm link drilldown sang hóa đơn
        java.time.LocalDate startDate = java.time.LocalDate.of(selectedYear, 1, 1);
        java.time.LocalDate endDate = java.time.LocalDate.of(selectedYear, 12, 31);

        // Đẩy năm chọn và khoảng thời gian vào Model cho View
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("startDate", startDate.toString());
        model.addAttribute("endDate", endDate.toString());

        // Chuẩn hóa nhãn hiển thị khoảng thời gian (ví dụ: "Năm 2026")
        String periodLabel = "Năm " + selectedYear;
        model.addAttribute("periodLabel", periodLabel);
        model.addAttribute("periodRevenueLabel", "Doanh thu Năm " + selectedYear);
        model.addAttribute("periodTicketsLabel", "Vé bán ra Năm " + selectedYear);

        // Danh sách các năm cho dropdown bộ lọc (Năm ngoái, Năm nay, Năm sau)
        List<Integer> availableYears = List.of(currentYear - 1, currentYear, currentYear + 1);
        model.addAttribute("availableYears", availableYears);

        // [LUỒNG XỬ LÝ 2: Đếm tổng số lượng thực thể hệ thống phục vụ hiển thị tổng quan]
        model.addAttribute("movieCount", movieRepository.count());
        model.addAttribute("roomCount", roomRepository.count());
        model.addAttribute("seatCount", seatRepository.count());
        model.addAttribute("showtimeCount", showtimeRepository.count());

        // [LUỒNG XỬ LÝ 3: Tra cứu tất cả đơn đặt vé và lọc đơn ĐÃ THANH TOÁN thuộc Năm được chọn]
        List<Booking> allBookings = bookingRepository.findAll();
        List<Booking> paidBookings = allBookings.stream()
                .filter(b -> b.getStatus() == Booking.Status.PAID)
                .collect(Collectors.toList());

        // Lọc danh sách đơn đã thanh toán thuộc chính xác selectedYear
        List<Booking> periodPaidBookings = paidBookings.stream()
                .filter(b -> {
                    if (b.getCreatedAt() == null) return false;
                    return b.getCreatedAt().getYear() == selectedYear;
                })
                .collect(Collectors.toList());

        // [LUỒNG XỬ LÝ 4: Tính Tổng số vé đã bán ra trong Năm chọn]
        long totalTicketsSold = periodPaidBookings.stream()
                .mapToLong(b -> bookingTicketRepository.findByBookingId(b.getId()).size())
                .sum();
        model.addAttribute("totalTicketsSold", totalTicketsSold);

        // [LUỒNG XỬ LÝ 5: Tính Tổng doanh thu thực nhận (VNĐ) từ các đơn đã thanh toán trong Năm chọn]
        double totalRevenue = periodPaidBookings.stream()
                .mapToDouble(b -> b.getTotalAmount() != null ? b.getTotalAmount().doubleValue() : 0.0)
                .sum();
        model.addAttribute("totalRevenue", totalRevenue);

        // [LUỒNG XỬ LÝ 6: Thống kê Top 5 Phim có doanh thu cao nhất trong Năm chọn]
        Map<String, MovieRevenueDto> movieStats = new HashMap<>();
        for (Booking booking : periodPaidBookings) {
            Optional<Showtime> showtime = showtimeRepository.findById(booking.getShowtimeId());
            String title = showtime.map(s -> s.getMovie().getTitle()).orElse("N/A");
            double revenue = booking.getTotalAmount() != null ? booking.getTotalAmount().doubleValue() : 0.0;
            long ticketCount = bookingTicketRepository.findByBookingId(booking.getId()).size();
            // Gom nhóm doanh thu và số vé bán theo tiêu đề phim
            movieStats.merge(title, new MovieRevenueDto(title, revenue, ticketCount), (oldVal, newVal) ->
                new MovieRevenueDto(title, oldVal.revenue() + newVal.revenue(), oldVal.ticketsSold() + newVal.ticketsSold())
            );
        }
        // Sắp xếp giảm dần theo doanh thu và lấy Top 5 phim hàng đầu
        List<MovieRevenueDto> topMovies = movieStats.values().stream()
                .sorted((m1, m2) -> Double.compare(m2.revenue(), m1.revenue()))
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("topMovies", topMovies);

        // [LUỒNG XỬ LÝ 7: Tính toán mảng doanh thu 12 tháng (Tháng 1 -> Tháng 12) của selectedYear cho Biểu đồ Đường]
        double[] monthlyRevenues = new double[12];
        for (Booking b : paidBookings) {
            if (b.getCreatedAt() != null && b.getCreatedAt().getYear() == selectedYear) {
                int mIdx = b.getCreatedAt().getMonthValue() - 1; // Tháng 1 = chỉ số 0
                if (mIdx >= 0 && mIdx < 12) {
                    double amt = b.getTotalAmount() != null ? b.getTotalAmount().doubleValue() : 0.0;
                    monthlyRevenues[mIdx] += amt;
                }
            }
        }
        model.addAttribute("monthlyRevenues", monthlyRevenues);

        // [LUỒNG XỬ LÝ 8: Lấy danh sách Lịch sử đặt vé gần đây thuộc selectedYear để hiển thị ở bảng phía dưới]
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<BookingDto> bookingHistory = allBookings.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().getYear() == selectedYear)
                .filter(b -> b.getStatus() == Booking.Status.PAID || b.getStatus() == Booking.Status.CANCELLED)
                .map(b -> {
                    String custName = accountRepository.findById(b.getAccountId())
                            .map(Account::getName)
                            .orElse("Khách vãng lai");
                    String movieTitle = showtimeRepository.findById(b.getShowtimeId())
                            .map(s -> s.getMovie().getTitle())
                            .orElse("N/A");
                    int ticketQty = bookingTicketRepository.findByBookingId(b.getId()).size();
                    String timeStr = b.getCreatedAt().format(formatter);
                    String statusLabel = switch (b.getStatus()) {
                        case PAID -> "Đã thanh toán";
                        case PENDING -> "Chờ thanh toán";
                        case CANCELLED -> "Đã hủy";
                        case EXPIRED -> "Hết hạn";
                    };
                    return new BookingDto(b.getId(), custName, movieTitle, ticketQty, b.getTotalAmount(), timeStr, statusLabel);
                })
                .sorted((b1, b2) -> b2.id().compareTo(b1.id())) // Ưu tiên giao dịch mới nhất xếp trước
                .collect(Collectors.toList());
        model.addAttribute("bookings", bookingHistory);

        // Đánh dấu menu active là "dashboard" trên thanh Sidebar và trả về template admin_dashboard.html
        model.addAttribute("active", "dashboard");
        return "admin_dashboard";
    }
}
