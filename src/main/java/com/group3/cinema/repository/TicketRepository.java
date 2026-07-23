/**
 * Interface Repository quản lý các bản ghi Vé xem phim đã được cấp (`tickets`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `TicketService`, `TicketManagementService`, `CustomerBookingService`, `TicketSeedInitializer`.
 * - Hỗ trợ các chức năng: Tra cứu vé theo người dùng (`findByAccountAccountIDOrderByBookingTimeDesc`), tính doanh thu suất chiếu (`calculateRevenueByShowtimeId`),
 *   tìm kiếm quản lý vé phía Admin (`searchTickets`), dọn dẹp vé chưa bán khi hủy suất chiếu (`deleteUnsoldTicketsByShowtimeId`),
 *   và kiểm tra ràng buộc không cho phép xóa suất chiếu/phim nếu đã có vé được bán out (`hasBookedTicketsForShowtime`, `hasBookedTicketsForMovie`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113, TrienLX
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Lấy lịch sử vé của tài khoản người dùng, sắp xếp mới nhất lên đầu. */
    List<Ticket> findByAccountAccountIDOrderByBookingTimeDesc(int accountId);

    /** Tìm chi tiết 1 vé theo ID vé và ID tài khoản sở hữu. */
    Optional<Ticket> findByIdAndAccountAccountID(Long id, int accountId);

    /** Tìm tất cả vé thuộc về 1 suất chiếu. */
    List<Ticket> findByShowtimeId(Long showtimeId);

    /** Tìm tất cả vé active (chưa bị xóa) của 1 suất chiếu. */
    List<Ticket> findByShowtimeIdAndDeletedFalse(Long showtimeId);

    /** Tìm vé active theo suất chiếu và ghế. */
    Optional<Ticket> findByShowtimeIdAndSeatIdAndDeletedFalse(Long showtimeId, Long seatId);

    /** Kiểm tra sự tồn tại của vé theo suất chiếu và trạng thái. */
    boolean existsByShowtimeIdAndStatus(Long showtimeId, String status);

    /** Kiểm tra sự tồn tại của vé active theo suất chiếu và trạng thái. */
    boolean existsByShowtimeIdAndStatusAndDeletedFalse(Long showtimeId, String status);

    /** Đếm tổng số vé của suất chiếu. */
    long countByShowtimeId(Long showtimeId);

    /** Đếm số lượng vé active của suất chiếu. */
    long countByShowtimeIdAndDeletedFalse(Long showtimeId);

    /** Đếm số lượng vé theo suất chiếu và trạng thái. */
    long countByShowtimeIdAndStatus(Long showtimeId, String status);

    /** Đếm số lượng vé active theo suất chiếu và trạng thái. */
    long countByShowtimeIdAndStatusAndDeletedFalse(Long showtimeId, String status);

    /**
     * Tính tổng doanh thu tiền vé thực tế (`finalPrice`) của các vé đã bán thành công (`BOOKED`) trong suất chiếu.
     */
    @Query("""
            SELECT COALESCE(SUM(t.finalPrice), 0)
            FROM Ticket t
            WHERE t.showtime.id = :showtimeId
              AND t.status = 'BOOKED'
              AND t.deleted = false
            """)
    Double calculateRevenueByShowtimeId(@Param("showtimeId") Long showtimeId);

    /**
     * Tìm kiếm danh sách vé đa tiêu chí cho giao diện quản lý vé của Admin/Manager.
     */
    @Query("""
            SELECT t
            FROM Ticket t
            WHERE t.deleted = false
              AND (:movieId IS NULL OR t.showtime.movie.id = :movieId)
              AND (:room IS NULL OR t.showtime.room = :room)
              AND (:status IS NULL OR t.status = :status)
              AND (:fromDate IS NULL OR t.showtime.showDate >= :fromDate)
              AND (:toDate IS NULL OR t.showtime.showDate <= :toDate)
              AND (:searchTerm IS NULL OR
                   CAST(t.id AS string) LIKE %:searchTerm% OR
                   t.seatNumber LIKE %:searchTerm%)
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<Ticket> searchTickets(@Param("movieId") Integer movieId,
                               @Param("room") String room,
                               @Param("status") String status,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               @Param("searchTerm") String searchTerm);

    /** Xóa các vé ở trạng thái 'Còn trống' thuộc suất chiếu. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status = 'Còn trống'")
    void deleteUnsoldTicketsByShowtimeId(@Param("showtimeId") Long showtimeId);

    /** Xóa toàn bộ vé thuộc suất chiếu. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Ticket t WHERE t.showtime.id = :showtimeId")
    void deleteAllByShowtimeId(@Param("showtimeId") Long showtimeId);

    /** Lấy danh sách tất cả các vé đã được đặt bán thành công. */
    @Query("SELECT t FROM Ticket t WHERE t.status IN ('BOOKED', 'CONFIRMED', 'PAID', 'USED') AND t.deleted = false")
    List<Ticket> findAllBookedTickets();

    /** Kiểm tra xem bộ phim đã có vé bán ra chưa (dùng chặn xóa phim). */
    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.showtime.movie.id = :movieId AND t.status IN ('BOOKED', 'CONFIRMED', 'PAID', 'USED') AND t.deleted = false")
    boolean hasBookedTicketsForMovie(@Param("movieId") Integer movieId);

    /** Kiểm tra xem suất chiếu đã có vé bán ra chưa (dùng chặn xóa suất chiếu). */
    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status IN ('BOOKED', 'CONFIRMED', 'PAID', 'USED') AND t.deleted = false")
    boolean hasBookedTicketsForShowtime(@Param("showtimeId") Long showtimeId);
}

