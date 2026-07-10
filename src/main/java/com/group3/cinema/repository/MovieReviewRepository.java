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

    List<MovieReview> findByMovieIdAndModerationStatusOrderByReviewDateDesc(
            int movieId,
            MovieReview.ModerationStatus moderationStatus
    );

    Page<MovieReview> findByMovieIdAndModerationStatusOrderByReviewDateDesc(
            int movieId,
            MovieReview.ModerationStatus moderationStatus,
            Pageable pageable
    );

    Page<MovieReview> findByMovieIdAndModerationStatusAndRatingScoreOrderByReviewDateDesc(
            int movieId,
            MovieReview.ModerationStatus moderationStatus,
            int ratingScore,
            Pageable pageable
    );

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

    List<MovieReview> findAllByOrderByReviewDateDesc();

    Optional<MovieReview> findByMovieIdAndAccountAccountID(int movieId, int accountId);

    long countByModerationStatus(MovieReview.ModerationStatus moderationStatus);

    @Query("""
            SELECT COALESCE(AVG(r.ratingScore), 0)
            FROM MovieReview r
            WHERE r.movie.id = :movieId
              AND r.moderationStatus = :status
            """)
    double averageRating(@Param("movieId") int movieId,
                         @Param("status") MovieReview.ModerationStatus status);

    @Query("""
            SELECT COUNT(r)
            FROM MovieReview r
            WHERE r.movie.id = :movieId
              AND r.moderationStatus = :status
            """)
    long reviewCount(@Param("movieId") int movieId,
                     @Param("status") MovieReview.ModerationStatus status);
}
