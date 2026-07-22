/**
 * Service tổng hợp dữ liệu Báo cáo Thống kê Doanh thu và Phân tích hiệu quả kinh doanh của rạp chiếu phim (`ReportService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ReportController` (Dashboard/Thống kê Admin).
 * - Tương tác với:
 *   + `BookingRepository`: Lấy đơn hàng đã thanh toán thành công trong mốc thời gian (`findByStatusAndPaidWindow`).
 *   + `BookingTicketRepository`: Thống kê số vé bán ra và doanh thu theo loại ghế (`findByBookingId`).
 *   + `BookingComboRepository`: Thống kê số lượng và tổng tiền bán gói combo bắp nước.
 *   + `ShowtimeRepository`: Tra cứu phòng chiếu và suất chiếu.
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final BookingRepository bookingRepository;
    private final BookingTicketRepository bookingTicketRepository;
    private final BookingComboRepository bookingComboRepository;
    private final ShowtimeRepository showtimeRepository;

    public ReportService(BookingRepository bookingRepository,
                         BookingTicketRepository bookingTicketRepository,
                         BookingComboRepository bookingComboRepository,
                         ShowtimeRepository showtimeRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingTicketRepository = bookingTicketRepository;
        this.bookingComboRepository = bookingComboRepository;
        this.showtimeRepository = showtimeRepository;
    }

    /**
     * Tổng hợp và phân tích biểu đồ doanh thu theo ngày, số lượng bán vé, doanh thu combo, loại ghế và phòng chiếu.
     * 
     * @param startDate Ngày bắt đầu thống kê.
     * @param endDate Ngày kết thúc thống kê.
     * @return Map dữ liệu báo cáo gồm `totalRevenue`, `paidBookingCount`, `totalTickets`, `dailyRevenue`, `comboStats`, `seatTypeStats`, `roomStats`.
     */
    public Map<String, Object> getRevenueAnalysis(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = (startDate != null ? startDate : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime end = (endDate != null ? endDate : LocalDate.now()).atTime(LocalTime.MAX);

        List<Booking> paidBookings = bookingRepository.findByStatusAndPaidWindow(Booking.Status.PAID, start, end);

        BigDecimal totalRevenue = paidBookings.stream()
                .map(Booking::getTotalAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalTickets = paidBookings.stream()
                .mapToLong(booking -> bookingTicketRepository.findByBookingId(booking.getId()).size())
                .sum();

        BigDecimal averageOrderValue = paidBookings.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(paidBookings.size()), 0, RoundingMode.HALF_UP);

        Map<LocalDate, StatBucket> dailyRevenue = new LinkedHashMap<>();
        Map<String, StatBucket> comboStats = new LinkedHashMap<>();
        Map<String, StatBucket> seatTypeStats = new LinkedHashMap<>();
        Map<String, StatBucket> roomStats = new LinkedHashMap<>();

        for (Booking booking : paidBookings) {
            BigDecimal bookingTotal = defaultAmount(booking.getTotalAmount());
            LocalDate paidDate = (booking.getPaidAt() != null ? booking.getPaidAt() : booking.getCreatedAt()).toLocalDate();
            dailyRevenue.computeIfAbsent(paidDate, ignored -> new StatBucket()).add(1, bookingTotal);

            Showtime showtime = showtimeRepository.findById(booking.getShowtimeId()).orElse(null);
            String roomName = showtime != null && showtime.getRoom() != null && !showtime.getRoom().isBlank()
                    ? showtime.getRoom()
                    : "Không xác định";

            List<BookingTicket> tickets = bookingTicketRepository.findByBookingId(booking.getId());
            roomStats.computeIfAbsent(roomName, ignored -> new StatBucket()).add(tickets.size(), bookingTotal);

            for (BookingTicket ticket : tickets) {
                String seatType = ticket.getSeatType() != null && !ticket.getSeatType().isBlank()
                        ? ticket.getSeatType()
                        : "Không xác định";
                seatTypeStats.computeIfAbsent(seatType, ignored -> new StatBucket()).add(1, defaultAmount(ticket.getPrice()));
            }

            for (BookingCombo combo : bookingComboRepository.findByBookingId(booking.getId())) {
                String comboName = combo.getComboName() != null && !combo.getComboName().isBlank()
                        ? combo.getComboName()
                        : "Combo không tên";
                comboStats.computeIfAbsent(comboName, ignored -> new StatBucket())
                        .add(combo.getQuantity() != null ? combo.getQuantity() : 0, defaultAmount(combo.getSubtotal()));
            }
        }

        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("totalRevenue", totalRevenue);
        reportData.put("paidBookingCount", paidBookings.size());
        reportData.put("totalTickets", totalTickets);
        reportData.put("averageOrderValue", averageOrderValue);
        reportData.put("dailyRevenue", toRowsInInsertionOrder(dailyRevenue));
        reportData.put("comboStats", toRows(comboStats));
        reportData.put("seatTypeStats", toRows(seatTypeStats));
        reportData.put("roomStats", toRows(roomStats));
        return reportData;
    }

    private static BigDecimal defaultAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private static List<Object[]> toRows(Map<?, StatBucket> source) {
        List<Object[]> rows = new ArrayList<>();
        source.forEach((label, bucket) -> rows.add(new Object[]{label, bucket.quantity, bucket.revenue}));
        rows.sort(Comparator.comparing(row -> row[2] instanceof BigDecimal amount ? amount : BigDecimal.ZERO,
                Comparator.reverseOrder()));
        return rows;
    }

    private static List<Object[]> toRowsInInsertionOrder(Map<?, StatBucket> source) {
        List<Object[]> rows = new ArrayList<>();
        source.forEach((label, bucket) -> rows.add(new Object[]{label, bucket.quantity, bucket.revenue}));
        return rows;
    }

    private static class StatBucket {
        private long quantity;
        private BigDecimal revenue = BigDecimal.ZERO;

        private void add(long quantity, BigDecimal revenue) {
            this.quantity += quantity;
            this.revenue = this.revenue.add(defaultAmount(revenue));
        }
    }
}

