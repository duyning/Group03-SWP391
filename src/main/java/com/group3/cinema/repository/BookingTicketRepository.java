/**
 * Repository quản lý ghế đang giữ/đã bán theo từng suất chiếu trong bảng `booking_tickets`.
 *
 * Luồng gọi & sử dụng:
 * - `BookingShowtimeService` đọc ghế đã chiếm để tính số chỗ còn lại.
 * - `SeatHoldingService.getSeatMap(...)` đọc theo showtimeId để biết ghế AVAILABLE/HOLDING/BOOKED.
 * - `SeatHoldingService.holdSeats(...)` đọc theo showtimeId + seatIds để chặn ghế đã bị giữ/bán.
 * - `CustomerBookingService` xác minh hold, gắn ghế vào booking PENDING và đọc chi tiết đơn.
 * - `CounterSaleService.completeSale(...)` đọc theo holdToken để đổi ghế HOLDING thành BOOKED.
 * - `CounterSaleService.createCounterPayment(...)` đọc theo holdToken để gắn ghế HOLDING vào Booking PENDING.
 * - `PaymentService` chuyển HOLDING thành BOOKED hoặc xóa ghế khi thanh toán hủy/hết hạn.
 * - Scheduler/flow dọn hạn dùng các hàm delete để giải phóng ghế HOLDING quá hạn.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.BookingTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingTicketRepository extends JpaRepository<BookingTicket, Long> {

    /** Tìm tất cả ghế đang HOLDING/BOOKED của một suất để render sơ đồ và tính số ghế còn bán. */
    List<BookingTicket> findByShowtimeId(Long showtimeId);

    /**
     * Tìm ghế đang thuộc một phiên giữ chỗ.
     *
     * Counter sale dùng khi preview/chốt đơn:
     * - Token từ bước chọn ghế được gửi sang checkout.
     * - Service lấy lại các dòng HOLDING theo token để tính tiền và chuyển sang BOOKED/PENDING.
     */
    List<BookingTicket> findByHoldToken(String holdToken);

    /**
     * Tìm danh sách ghế thuộc về một đơn đặt vé (`bookingId`).
     */
    List<BookingTicket> findByBookingId(Long bookingId);

    /**
     * Tìm các bản ghi của một tập ghế trong một suất.
     *
     * `SeatHoldingService.holdSeats(...)` dùng để phát hiện xung đột trước khi insert hold mới:
     * ghế đã BOOKED, đã gắn bookingId hoặc đang HOLDING bởi token khác đều bị chặn.
     */
    List<BookingTicket> findByShowtimeIdAndSeatIdIn(Long showtimeId, Collection<Long> seatIds);

    /** Xóa các ghế HOLDING đã hết hạn để trả lại ghế về trạng thái có thể bán. */
    int deleteByStatusAndHoldExpiresAtBefore(BookingTicket.Status status, LocalDateTime now);

    /**
     * Xóa tất cả ghế liên quan tới ID đơn đặt vé.
     */
    int deleteByBookingId(Long bookingId);

    /**
     * Xóa các ghế giữ tạm theo token khi chưa tạo booking thật.
     *
     * Dùng khi nhân viên đổi ghế/quay lại màn chọn ghế. Điều kiện `bookingId IS NULL`
     * bảo vệ các ghế đã gắn với đơn PENDING/PAID khỏi bị xóa nhầm.
     */
    @Modifying
    @Query("DELETE FROM BookingTicket t WHERE t.holdToken = :token AND t.bookingId IS NULL")
    int deleteUnbookedByHoldToken(@Param("token") String token);

    /**
     * Kiểm tra xem bộ phim có chứa bất kỳ bản ghi giữ ghế hoặc bán vé nào đang active trong booking_tickets hay không.
     */
    @Query("""
            SELECT COUNT(bt) > 0 
            FROM BookingTicket bt 
            JOIN Showtime s ON s.id = bt.showtimeId 
            WHERE s.movie.id = :movieId 
              AND (bt.status = com.group3.cinema.entity.BookingTicket$Status.BOOKED 
                   OR (bt.status = com.group3.cinema.entity.BookingTicket$Status.HOLDING AND bt.holdExpiresAt > :now))
            """)
    boolean hasActiveHoldingsOrBookingsForMovie(@Param("movieId") Integer movieId, @Param("now") LocalDateTime now);

    /**
     * Kiểm tra xem suất chiếu có chứa bất kỳ bản ghi giữ ghế hoặc bán vé nào đang active trong booking_tickets hay không.
     */
    @Query("""
            SELECT COUNT(bt) > 0 
            FROM BookingTicket bt 
            WHERE bt.showtimeId = :showtimeId 
              AND (bt.status = com.group3.cinema.entity.BookingTicket$Status.BOOKED 
                   OR (bt.status = com.group3.cinema.entity.BookingTicket$Status.HOLDING AND bt.holdExpiresAt > :now))
            """)
    boolean hasActiveHoldingsOrBookingsForShowtime(@Param("showtimeId") Long showtimeId, @Param("now") LocalDateTime now);
}

