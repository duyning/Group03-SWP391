/**
 * Interface Repository quản lý các bản ghi ghế giữ chỗ và ghế đã bán theo từng suất chiếu (`booking_tickets`).
 * 
 * Luồng gọi & Sử dụng:
 * - `BookingShowtimeService` đọc ghế đã chiếm để tính số chỗ còn lại.
 * - `SeatHoldingService` tạo/xóa hold, phát hiện tranh chấp và dựng trạng thái sơ đồ ghế.
 * - `CustomerBookingService` xác minh hold, gắn ghế vào booking PENDING và đọc chi tiết đơn.
 * - `PaymentService` chuyển HOLDING thành BOOKED hoặc xóa ghế khi thanh toán hủy/hết hạn.
 * - Scheduler dọn các bản ghi HOLDING quá hạn.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
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

    /**
     * Tìm danh sách tất cả ghế đang giữ/đã mua theo ID suất chiếu.
     */
    List<BookingTicket> findByShowtimeId(Long showtimeId);

    /**
     * Tìm danh sách ghế giữ tạm theo mã Token giữ chỗ (`holdToken`).
     */
    List<BookingTicket> findByHoldToken(String holdToken);

    /**
     * Tìm danh sách ghế thuộc về một đơn đặt vé (`bookingId`).
     */
    List<BookingTicket> findByBookingId(Long bookingId);

    /**
     * Tìm các bản ghi giữ chỗ theo suất chiếu và tập hợp ID ghế.
     */
    List<BookingTicket> findByShowtimeIdAndSeatIdIn(Long showtimeId, Collection<Long> seatIds);

    /**
     * Xóa các bản ghi giữ ghế ở trạng thái chỉ định (HOLDING) mà thời hạn hết hạn nhỏ hơn thời điểm hiện tại (`now`).
     */
    int deleteByStatusAndHoldExpiresAtBefore(BookingTicket.Status status, LocalDateTime now);

    /**
     * Xóa tất cả ghế liên quan tới ID đơn đặt vé.
     */
    int deleteByBookingId(Long bookingId);

    /**
     * Xóa các giữ ghế theo token mà chưa hoàn tất gắn với đơn đặt vé (`bookingId IS NULL`).
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

