/**
 * Interface Repository quản lý thông tin Đơn hàng đặt vé tổng hợp (`customer_bookings`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CustomerBookingService`, `TicketManagementService`, `ReportService`, `MovieRecommendationService`, `MovieReviewService`.
 * - Hỗ trợ các chức năng: Tìm kiếm đơn theo trạng thái và thời gian hết hạn (`findByStatusAndExpiresAtBefore`),
 *   quản lý danh sách đơn hàng đã đặt của người dùng, tìm kiếm hóa đơn bán vé (`searchInvoices`),
 *   kiểm tra xem người dùng đã từng xem bộ phim nào chưa (`existsWatchedMovie`, `findWatchedBookings`),
 *   thống kê lịch sử sở thích phim để gợi ý phim mới (`findPaidMovieIdsByAccount`, `findPaidMovieGenresByAccount`),
 *   và kiểm tra ràng buộc không cho phép xóa suất chiếu/phim nếu đã có vé giữ/đã thanh toán (`hasActiveBookingsForShowtime`, `hasActiveBookingsForMovie`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Tìm đơn đặt vé theo ID đơn hàng và ID tài khoản sở hữu.
     */
    Optional<Booking> findByIdAndAccountId(Long id, Integer accountId);

    /**
     * Tìm tất cả đơn đặt vé ở trạng thái PENDING nhưng đã quá thời hạn giữ chỗ (`expiresAt < now`).
     * Phục vụ Scheduler dọn dẹp giữ ghế tự động.
     */
    List<Booking> findByStatusAndExpiresAtBefore(Booking.Status status, LocalDateTime expiresAt);

    /**
     * Đếm số lượt sử dụng voucher của một tài khoản theo các trạng thái đơn hàng chỉ định (dùng để kiểm tra giới hạn lượt dùng `limitPerUser`).
     */
    long countByAccountIdAndVoucherCodeAndStatusIn(Integer accountId, String voucherCode, List<Booking.Status> statuses);

    /**
     * Lấy danh sách lịch sử đơn đặt vé của một tài khoản, sắp xếp giảm dần theo thời gian tạo.
     */
    List<Booking> findByAccountIdOrderByCreatedAtDesc(Integer accountId);

    /**
     * Lấy danh sách đơn đặt vé đã thanh toán thành công nằm trong khoảng thời gian xác định cho báo cáo tài chính.
     */
    @Query("""
            SELECT b
            FROM Booking b
            WHERE b.status = :status
              AND ((b.paidAt IS NOT NULL AND b.paidAt BETWEEN :fromDate AND :toDate)
                   OR (b.paidAt IS NULL AND b.createdAt BETWEEN :fromDate AND :toDate))
            ORDER BY b.paidAt ASC, b.createdAt ASC
            """)
    List<Booking> findByStatusAndPaidWindow(@Param("status") Booking.Status status,
                                            @Param("fromDate") LocalDateTime fromDate,
                                            @Param("toDate") LocalDateTime toDate);

    /**
     * Tìm kiếm và tra cứu hóa đơn bán vé dành cho Admin/Manager.
     * Hỗ trợ lọc đa điều kiện: từ khóa (mã voucher, mã orderCode, tên/email/sđt khách), trạng thái đơn, trạng thái thanh toán, phương thức thanh toán, từ ngày - đến ngày.
     */
    @Query("""
            SELECT b
            FROM Booking b
            WHERE (:status IS NULL OR b.status = :status)
              AND (:fromDate IS NULL OR b.createdAt >= :fromDate)
              AND (:toDate IS NULL OR b.createdAt <= :toDate)
              AND (:paymentStatus IS NULL OR EXISTS (
                    SELECT p.id FROM Payment p
                    WHERE p.bookingId = b.id AND p.status = :paymentStatus
              ))
              AND (:paymentMethod IS NULL OR EXISTS (
                    SELECT p.id FROM Payment p
                    WHERE p.bookingId = b.id AND p.paymentMethod = :paymentMethod
              ))
              AND (:bookingId IS NULL OR b.id = :bookingId)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(COALESCE(b.voucherCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR EXISTS (
                        SELECT p.id FROM Payment p
                        WHERE p.bookingId = b.id
                          AND LOWER(p.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   )
                   OR EXISTS (
                        SELECT a.accountID FROM Account a
                        WHERE a.accountID = b.accountId
                          AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                               OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                               OR a.phoneNum LIKE CONCAT('%', :keyword, '%'))
                   ))
            ORDER BY b.createdAt DESC
            """)
    List<Booking> searchInvoices(@Param("keyword") String keyword,
                                 @Param("bookingId") Long bookingId,
                                 @Param("status") Booking.Status status,
                                 @Param("paymentStatus") com.group3.cinema.entity.Payment.Status paymentStatus,
                                 @Param("paymentMethod") com.group3.cinema.entity.Payment.Method paymentMethod,
                                 @Param("fromDate") LocalDateTime fromDate,
                                 @Param("toDate") LocalDateTime toDate);

    /**
     * Kiểm tra xem khách hàng đã thực sự xem bộ phim này hay chưa (đã thanh toán vé và thời điểm suất chiếu đã diễn ra).
     * Dùng để điều kiện hóa quyền viết đánh giá/bình luận phim.
     */
    @Query(value = """
            SELECT CAST(CASE WHEN COUNT_BIG(b.id) > 0 THEN 1 ELSE 0 END AS bit)
            FROM customer_bookings b
            JOIN showtimes s ON s.id = b.showtime_id
            WHERE b.account_id = :accountId
              AND b.status = :paidStatus
              AND s.movie_id = :movieId
              AND (s.show_date < :today OR (s.show_date = :today AND s.show_time <= CAST(:now AS time)))
            """, nativeQuery = true)
    boolean existsWatchedMovie(@Param("accountId") Integer accountId,
                               @Param("movieId") Integer movieId,
                               @Param("paidStatus") String paidStatus,
                               @Param("today") LocalDate today,
                               @Param("now") LocalTime now);

    /**
     * Lấy các bản ghi đơn đặt vé cho phim mà khách hàng đã thực sự xem.
     */
    @Query(value = """
            SELECT b.*
            FROM customer_bookings b
            JOIN showtimes s ON s.id = b.showtime_id
            WHERE b.account_id = :accountId
              AND b.status = :paidStatus
              AND s.movie_id = :movieId
              AND (s.show_date < :today OR (s.show_date = :today AND s.show_time <= CAST(:now AS time)))
            ORDER BY s.show_date DESC, s.show_time DESC
            """, nativeQuery = true)
    List<Booking> findWatchedBookings(@Param("accountId") Integer accountId,
                                      @Param("movieId") Integer movieId,
                                      @Param("paidStatus") String paidStatus,
                                      @Param("today") LocalDate today,
                                      @Param("now") LocalTime now);

    /**
     * Lấy danh sách ID các phim mà tài khoản đã mua vé thành công (phục vụ thuật toán gợi ý phim).
     */
    @Query("""
            SELECT DISTINCT s.movie.id
            FROM Booking b
            JOIN Showtime s ON s.id = b.showtimeId
            WHERE b.accountId = :accountId
              AND b.status = :paidStatus
            """)
    List<Integer> findPaidMovieIdsByAccount(@Param("accountId") Integer accountId,
                                            @Param("paidStatus") Booking.Status paidStatus);

    /**
     * Lấy danh sách thể loại phim mà tài khoản đã mua vé nhiều nhất (phục vụ gợi ý phim theo sở thích).
     */
    @Query("""
            SELECT s.movie.genre
            FROM Booking b
            JOIN Showtime s ON s.id = b.showtimeId
            WHERE b.accountId = :accountId
              AND b.status = :paidStatus
              AND s.movie.genre IS NOT NULL
              AND s.movie.genre <> ''
            """)
    List<String> findPaidMovieGenresByAccount(@Param("accountId") Integer accountId,
                                              @Param("paidStatus") Booking.Status paidStatus);

    /**
     * Thống kê tổng số lượt đặt vé thành công theo từng bộ phim.
     */
    @Query("""
            SELECT s.movie.id, COUNT(b)
            FROM Booking b
            JOIN Showtime s ON s.id = b.showtimeId
            WHERE b.status = :paidStatus
            GROUP BY s.movie.id
            """)
    List<Object[]> countPaidBookingsByMovie(@Param("paidStatus") Booking.Status paidStatus);

    /**
     * Kiểm tra xem suất chiếu có chứa đơn đặt vé nào đang active (PAID hoặc PENDING còn hạn) hay không.
     * Ngăn chặn việc xóa suất chiếu đã có khách giữ chỗ/mua vé.
     */
    @Query("""
            SELECT COUNT(b) > 0 
            FROM Booking b 
            WHERE b.showtimeId = :showtimeId 
              AND (b.status = com.group3.cinema.entity.Booking$Status.PAID 
                   OR (b.status = com.group3.cinema.entity.Booking$Status.PENDING AND b.expiresAt > :now))
            """)
    boolean hasActiveBookingsForShowtime(@Param("showtimeId") Long showtimeId, @Param("now") LocalDateTime now);

    /**
     * Kiểm tra xem bộ phim có chứa bất kỳ đơn đặt vé active nào hay không.
     * Ngăn chặn việc xóa phim đã có vé được bán ra.
     */
    @Query("""
            SELECT COUNT(b) > 0 
            FROM Booking b 
            JOIN Showtime s ON s.id = b.showtimeId 
            WHERE s.movie.id = :movieId 
              AND (b.status = com.group3.cinema.entity.Booking$Status.PAID 
                   OR (b.status = com.group3.cinema.entity.Booking$Status.PENDING AND b.expiresAt > :now))
            """)
    boolean hasActiveBookingsForMovie(@Param("movieId") Integer movieId, @Param("now") LocalDateTime now);
}

