package com.swp392.cinema2026.repository;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: ShowtimeRepository.java
 * Chức năng: Lớp giao diện (Interface) Repository quản lý việc truy xuất dữ liệu của bảng "showtimes"
 *            thông qua Spring Data JPA. Hỗ trợ thao tác CRUD cơ bản và định nghĩa truy vấn tìm kiếm
 *            lịch chiếu nâng cao dựa trên nhiều tiêu chí (ID phim, loại ngày, khoảng thời gian).
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import com.swp392.cinema2026.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// Đánh dấu đây là một repository xử lý dữ liệu cho đối tượng Showtime
@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    // Định nghĩa truy vấn JPQL động giúp lọc tìm kiếm lịch chiếu theo nhiều tiêu chí tùy chọn cùng lúc
    @Query("SELECT s FROM Showtime s WHERE " +
           "(:movieId IS NULL OR s.movie.id = :movieId) AND " +
           "(:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType) AND " +
           "(:startDate IS NULL OR s.showDate >= :startDate) AND " +
           "(:endDate IS NULL OR s.showDate <= :endDate) " +
           "ORDER BY s.showDate ASC, s.showTime ASC")
    List<Showtime> searchShowtimes(
            @Param("movieId") Long movieId,
            @Param("dayType") String dayType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Truy vấn tự động của JPA đếm số lượng lịch chiếu theo loại ngày cụ thể
    long countByDayType(String dayType);

    List<Showtime> findByRoomIgnoreCaseAndShowDate(String room, LocalDate showDate);
}
