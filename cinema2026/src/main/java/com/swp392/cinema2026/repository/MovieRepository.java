package com.swp392.cinema2026.repository;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: MovieRepository.java
 * Chức năng: Lớp giao diện (Interface) Repository quản lý việc truy xuất dữ liệu của bảng "movies"
 *            thông qua Spring Data JPA. Hỗ trợ thao tác CRUD cơ bản và định nghĩa truy vấn tìm kiếm
 *            phim nâng cao dựa trên nhiều tiêu chí (tên, thể loại, đạo diễn, thời lượng, trạng thái, ngày ra mắt).
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import com.swp392.cinema2026.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// Đánh dấu đây là một repository xử lý dữ liệu cho đối tượng Movie
@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    // Định nghĩa truy vấn JPQL động hỗ trợ lọc tìm kiếm phim nhiều tham số
    // Các tham số truyền vào nếu là null hoặc chuỗi rỗng sẽ được bỏ qua trong biểu thức so sánh
    @Query("SELECT m FROM Movie m WHERE " +
           "(:title IS NULL OR :title = '' OR LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:genre IS NULL OR :genre = '' OR LOWER(m.genre) LIKE LOWER(CONCAT('%', :genre, '%'))) AND " +
           "(:director IS NULL OR :director = '' OR LOWER(m.director) LIKE LOWER(CONCAT('%', :director, '%'))) AND " +
           "(:duration IS NULL OR m.duration = :duration) AND " +
           "(:status IS NULL OR :status = '' OR m.status = :status) AND " +
           "(:releaseDate IS NULL OR m.releaseDate = :releaseDate)")
    List<Movie> searchMovies(
            @Param("title") String title,
            @Param("genre") String genre,
            @Param("director") String director,
            @Param("duration") Integer duration,
            @Param("status") String status,
            @Param("releaseDate") LocalDate releaseDate
    );

    // Phương thức Spring Data JPA tự động sinh câu truy vấn đếm số lượng phim theo trạng thái cụ thể
    long countByStatus(String status);
}
