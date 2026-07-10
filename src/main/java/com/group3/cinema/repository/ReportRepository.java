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

    // 1. Tổng doanh thu theo khoảng thời gian (Ngày/Tháng/Năm)
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' AND p.createdAt BETWEEN :start AND :end")
    Double sumRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 2. Doanh thu theo phương thức thanh toán (VNPAY, MOMO, PAYOS)
    @Query("SELECT p.paymentMethod, SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS' GROUP BY p.paymentMethod")
    List<Object[]> sumRevenueByPaymentMethod();

    // 3. Doanh thu theo PHIM (Dùng Subquery né bẫy nhân đôi dòng dữ liệu của Ticket)
    @Query("SELECT s.movie.title, SUM(p.amount) FROM Payment p " +
            "JOIN Showtime s ON s.id = (SELECT DISTINCT t.showtimeId FROM BookingTicket t WHERE t.bookingId = p.bookingId) " +
            "WHERE p.status = 'SUCCESS' GROUP BY s.movie.title")
    List<Object[]> sumRevenueByMovie();

    // 4. Doanh thu theo COMBO (Đồ ăn thức uống từ bảng booking_combos)
    @Query("SELECT c.comboName, SUM(c.quantity), SUM(c.subtotal) FROM BookingCombo c GROUP BY c.comboName")
    List<Object[]> sumRevenueByCombo();

    // 5. Thống kê số lượng vé bán ra và doanh thu theo LOẠI GHẾ (Vip, Thường...)
    @Query("SELECT t.seatType, COUNT(t), SUM(t.price) FROM BookingTicket t WHERE t.status = 'BOOKED' GROUP BY t.seatType")
    List<Object[]> sumRevenueBySeatType();
}