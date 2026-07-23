/**
 * Interface Repository quản lý thông tin Suất chiếu phim (`showtimes`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ShowtimeService`, `CustomerBookingService`, `TicketManagementService`, `PublicContentInitializer`, `MovieService`.
 * - Hỗ trợ các chức năng: Tìm kiếm suất chiếu quản lý phía Admin (`searchShowtimes`), tìm suất chiếu khả dụng phía Khách hàng (`searchShowtimesForCustomer`),
 *   kiểm tra trùng lịch suất chiếu theo phòng và ngày chiếu (`findByRoomIgnoreCaseAndShowDate`),
 *   lấy danh sách suất chiếu của một phim theo ngày (`findByMovieIdAndShowDate`),
 *   thống kê số lượng suất chiếu hiện tại và tương lai của bộ phim (`countAllShowtimesByMovieId`, `countFutureShowtimesByMovieId`).
 * 
 * Khởi tạo bởi: TrienLX - HE182285, NinhDD - HE186113
 */
package com.group3.cinema.repository.api;

import com.group3.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    /** Lấy tất cả các suất chiếu đang hoạt động (`active = true`). */
    @Override
    @Query("SELECT s FROM Showtime s WHERE s.active = true")
    List<Showtime> findAll();

    /** Đếm tổng số suất chiếu đang hoạt động. */
    @Override
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.active = true")
    long count();

    /**
     * Tìm kiếm danh sách suất chiếu theo phim, loại ngày (Trong tuần/Cuối tuần/Ngày lễ) và khoảng ngày chiếu cho Admin.
     */
    @Query("""
            SELECT s
            FROM Showtime s
            WHERE s.active = true
              AND (:movieId IS NULL OR s.movie.id = :movieId)
              AND (:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType)
              AND (:startDate IS NULL OR s.showDate >= :startDate)
              AND (:endDate IS NULL OR s.showDate <= :endDate)
            ORDER BY s.showDate ASC, s.showTime ASC
            """)
    List<Showtime> searchShowtimes(@Param("movieId") Integer movieId,
                                   @Param("dayType") String dayType,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    /**
     * Tìm kiếm danh sách suất chiếu khả dụng hiển thị phía Khách hàng (loại bỏ các phim bị dừng chiếu `STOPPED` hoặc bị ẩn `active = false`).
     */
    @Query("""
            SELECT s
            FROM Showtime s
            WHERE s.active = true
              AND s.movie.active = true
              AND s.movie.status <> com.group3.cinema.entity.Movie$MovieStatus.STOPPED
              AND (:movieId IS NULL OR s.movie.id = :movieId)
              AND (:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType)
              AND (:startDate IS NULL OR s.showDate >= :startDate)
              AND (:endDate IS NULL OR s.showDate <= :endDate)
            ORDER BY s.showDate ASC, s.showTime ASC
            """)
    List<Showtime> searchShowtimesForCustomer(@Param("movieId") Integer movieId,
                                             @Param("dayType") String dayType,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /** Đếm số lượng suất chiếu active theo loại ngày. */
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.dayType = :dayType AND s.active = true")
    long countByDayType(@Param("dayType") String dayType);

    /** Kiểm tra xem phòng chiếu có suất chiếu nào từ ngày `showDate` trở về sau không. */
    boolean existsByRoomIgnoreCaseAndShowDateGreaterThanEqual(String room, LocalDate showDate);

    /**
     * Lấy các suất chiếu đang hoạt động trong 1 phòng chiếu vào một ngày chỉ định (dùng để kiểm tra trùng khung giờ chiếu).
     */
    @Query("""
            SELECT s
            FROM Showtime s
            WHERE LOWER(s.room) = LOWER(:room)
              AND s.showDate = :showDate
              AND s.active = true
            """)
    List<Showtime> findByRoomIgnoreCaseAndShowDate(@Param("room") String room,
                                                   @Param("showDate") LocalDate showDate);

    /** Đếm tổng số suất chiếu thuộc tên phòng chiếu. */
    long countByRoomIgnoreCase(String room);

    /** Đếm số lượng suất chiếu diễn ra trong một ngày chỉ định. */
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.showDate = :showDate AND s.active = true")
    long countByShowDate(@Param("showDate") LocalDate showDate);

    /** Đếm số lượng suất chiếu diễn ra sau một ngày chỉ định. */
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.showDate > :showDate AND s.active = true")
    long countByShowDateGreaterThan(@Param("showDate") LocalDate showDate);

    /** Đếm số lượng suất chiếu đã kết thúc trước một ngày chỉ định. */
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.showDate < :showDate AND s.active = true")
    long countByShowDateLessThan(@Param("showDate") LocalDate showDate);

    /**
     * Lấy danh sách suất chiếu của một phim vào ngày chỉ định phục vụ cho quy trình đặt vé của khách hàng.
     */
    @Query("""
            SELECT s
            FROM Showtime s
            WHERE s.movie.id = :movieId
              AND s.showDate = :showDate
              AND s.active = true
            """)
    List<Showtime> findByMovieIdAndShowDate(@Param("movieId") int movieId,
                                            @Param("showDate") LocalDate showDate);

    /** Đếm tất cả suất chiếu active của một bộ phim. */
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.movie.id = :movieId AND s.active = true")
    long countAllShowtimesByMovieId(@Param("movieId") int movieId);

    /** Đếm các suất chiếu từ ngày hôm nay trở đi (`>= today`) của bộ phim. */
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.movie.id = :movieId AND s.showDate >= :today AND s.active = true")
    long countFutureShowtimesByMovieId(@Param("movieId") int movieId, @Param("today") LocalDate today);
}

