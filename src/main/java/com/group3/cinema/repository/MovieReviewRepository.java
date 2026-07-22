/**
 * Interface Repository quản lý các bản ghi Đánh giá & Bình luận phim (`movie_reviews`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `MovieReviewService` để xử lý việc viết đánh giá, duyệt/ẩn bình luận phía Admin,
 *   tính điểm đánh giá trung bình (`averageRating`), đếm tổng số đánh giá (`reviewCount`) và hiển thị phân trang đánh giá tại trang chi tiết phim.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (10/07/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.MovieReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieReviewRepository extends JpaRepository<MovieReview, Long> {

    /**
     * Lấy danh sách đánh giá đã duyệt của phim, sắp xếp giảm dần theo thời gian.
     */
    List<MovieReview> findByMovieIdAndModerationStatusOrderByReviewDateDesc(
            int movieId,
            MovieReview.ModerationStatus moderationStatus
    );

    /**
     * Lấy danh sách đánh giá có phân trang theo phim và trạng thái duyệt.
     */
    Page<MovieReview> findByMovieIdAndModerationStatusOrderByReviewDateDesc(
            int movieId,
            MovieReview.ModerationStatus moderationStatus,
            Pageable pageable
    );

    /**
     * Lấy danh sách đánh giá có phân trang theo số sao đánh giá (`ratingScore`).
     */
    Page<MovieReview> findByMovieIdAndModerationStatusAndRatingScoreOrderByReviewDateDesc(
            int movieId,
            MovieReview.ModerationStatus moderationStatus,
            int ratingScore,
            Pageable pageable
    );

    /**
     * Truy vấn tìm kiếm danh sách bình luận/đánh giá công khai của phim có hỗ trợ lọc theo số sao và khoảng ngày đánh giá.
     * 
     * @param movieId ID bộ phim.
     * @param status Trạng thái duyệt (thường là APPROVED).
     * @param ratingScore Số sao lọc (1..5 / null).
     * @param startDate Ngày bắt đầu lọc.
     * @param endDate Ngày kết thúc lọc.
     * @param pageable Đối tượng phân trang.
     * @return Trang danh sách MovieReview.
     */
    @Query("""
            SELECT r
            FROM MovieReview r
            WHERE r.movie.id = :movieId
              AND r.moderationStatus = :status
              AND (:ratingScore IS NULL OR r.ratingScore = :ratingScore)
              AND (:startDate IS NULL OR r.reviewDate >= :startDate)
              AND (:endDate IS NULL OR r.reviewDate <= :endDate)
            ORDER BY r.reviewDate DESC
            """)
    Page<MovieReview> searchVisibleReviews(
            @Param("movieId") int movieId,
            @Param("status") MovieReview.ModerationStatus status,
            @Param("ratingScore") Integer ratingScore,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Lấy tất cả các đánh giá trong toàn hệ thống phục vụ trang quản lý bình luận của Admin.
     */
    List<MovieReview> findAllByOrderByReviewDateDesc();

    /**
     * Tìm bài đánh giá của một tài khoản cụ thể đối với một bộ phim chỉ định.
     */
    Optional<MovieReview> findByMovieIdAndAccountAccountID(int movieId, int accountId);

    /**
     * Đếm số lượt đánh giá theo trạng thái kiểm duyệt.
     */
    long countByModerationStatus(MovieReview.ModerationStatus moderationStatus);

    /**
     * Tính số điểm đánh giá trung bình (average rating) của bộ phim dựa trên các bản ghi đã duyệt.
     */
    @Query("""
            SELECT COALESCE(AVG(r.ratingScore), 0)
            FROM MovieReview r
            WHERE r.movie.id = :movieId
              AND r.moderationStatus = :status
            """)
    double averageRating(@Param("movieId") int movieId,
                         @Param("status") MovieReview.ModerationStatus status);

    /**
     * Đếm tổng số lượt đánh giá công khai của một bộ phim.
     */
    @Query("""
            SELECT COUNT(r)
            FROM MovieReview r
            WHERE r.movie.id = :movieId
              AND r.moderationStatus = :status
            """)
    long reviewCount(@Param("movieId") int movieId,
                     @Param("status") MovieReview.ModerationStatus status);
}

