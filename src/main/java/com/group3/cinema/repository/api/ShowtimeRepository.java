package com.group3.cinema.repository.api;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeRepository.java
 * Chức năng: Lớp giao diện (Interface) Repository quản lý việc truy xuất dữ liệu của bảng "showtimes"
 *            thông qua Spring Data JPA. Hỗ trợ thao tác CRUD cơ bản và định nghĩa truy vấn tìm kiếm
 *            lịch chiếu nâng cao dựa trên nhiều tiêu chí (ID phim, loại ngày, khoảng thời gian).
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-23
 * Chi tiết thay đổi:
 * - [SỬA - TrienLX - 2026-06-23] Thêm query findByMovieIdAndShowDate để tìm bản ghi gốc theo
 *   movieId + ngày khi thực hiện override 1 ngày cụ thể (thay vì tạo bản ghi mới).
 */

import com.group3.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// ÄÃ¡nh dáº¥u Ä‘Ã¢y lÃ  má»™t repository xá»­ lÃ½ dá»¯ liá»‡u cho Ä‘á»‘i tÆ°á»£ng Showtime
@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    // Äá»‹nh nghÄ©a truy váº¥n JPQL Ä‘á»™ng giÃºp lá»c tÃ¬m kiáº¿m lá»‹ch chiáº¿u theo nhiá»u tiÃªu chÃ­ tÃ¹y chá»n cÃ¹ng lÃºc
    @Query("SELECT s FROM Showtime s WHERE " +
           "(:movieId IS NULL OR s.movie.id = :movieId) AND " +
           "(:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType) AND " +
           "(:startDate IS NULL OR s.showDate >= :startDate) AND " +
           "(:endDate IS NULL OR s.showDate <= :endDate) " +
           "ORDER BY s.showDate ASC, s.showTime ASC")
    List<Showtime> searchShowtimes(
            @Param("movieId") Integer movieId,
            @Param("dayType") String dayType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Truy váº¥n tá»± Ä‘á»™ng cá»§a JPA Ä‘áº¿m sá»‘ lÆ°á»£ng lá»‹ch chiáº¿u theo loáº¡i ngÃ y cá»¥ thá»ƒ
    long countByDayType(String dayType);

    boolean existsByRoomIgnoreCaseAndShowDateGreaterThanEqual(String room, LocalDate showDate);

    List<Showtime> findByRoomIgnoreCaseAndShowDate(String room, LocalDate showDate);

    long countByRoomIgnoreCase(String room);

    long countByShowDate(LocalDate showDate);

    long countByShowDateGreaterThan(LocalDate showDate);

    long countByShowDateLessThan(LocalDate showDate);

    // [SỬA - TrienLX - 2026-06-23]
    // Tìm suất chiếu theo movieId và ngày chiếu để thực hiện override (cập nhật bản ghi gốc thay vì tạo mới).
    // Trả về danh sách vì một ngày có thể có nhiều suất chiếu (nhiều slot).
    List<Showtime> findByMovieIdAndShowDate(int movieId, LocalDate showDate);
}
