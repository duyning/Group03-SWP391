/**
 * Interface Repository truy vấn các báo cáo thống kê doanh thu tài chính rạp chiếu (`payments`, `booking_combos`, `booking_tickets`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ReportService` và `AdminController` để xuất biểu đồ báo cáo doanh thu theo phim, thời gian, phương thức thanh toán, combo và loại ghế.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Payment, Long> {

    /**
     * Tính tổng doanh thu giao dịch thành công (`SUCCESS`) nằm trong khoảng thời gian từ `start` đến `end`.
     */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' AND p.createdAt BETWEEN :start AND :end")
    Double sumRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Thống kê tổng doanh thu phân nhóm theo các cổng thanh toán trực tuyến (PayOS, VNPay, MoMo).
     */
    @Query("SELECT p.paymentMethod, SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' GROUP BY p.paymentMethod")
    List<Object[]> sumRevenueByPaymentMethod();

    /**
     * Thống kê tổng doanh thu theo từng bộ phim chiếu rạp.
     */
    @Query("SELECT s.movie.title, SUM(p.amount) FROM Payment p " +
            "JOIN Showtime s ON s.id = (SELECT DISTINCT t.showtimeId FROM BookingTicket t WHERE t.bookingId = p.bookingId) " +
            "WHERE p.status = 'SUCCESS' GROUP BY s.movie.title")
    List<Object[]> sumRevenueByMovie();

    /**
     * Thống kê tổng số lượng bán ra và tổng tiền thu được theo từng gói Combo bắp nước.
     */
    @Query("SELECT c.comboName, SUM(c.quantity), SUM(c.subtotal) FROM BookingCombo c GROUP BY c.comboName")
    List<Object[]> sumRevenueByCombo();

    /**
     * Thống kê tổng số lượng vé đã bán ra và doanh thu phân theo loại ghế (std, vip, couple).
     */
    @Query("SELECT t.seatType, COUNT(t), SUM(t.price) FROM BookingTicket t WHERE t.status = 'BOOKED' GROUP BY t.seatType")
    List<Object[]> sumRevenueBySeatType();
}