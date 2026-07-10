package com.group3.cinema.repository;

/*
 * Added on 2026-06-24: Repository for customer booking records.
 * Created by: HuyPB - HE191335
 */

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
    Optional<Booking> findByIdAndAccountId(Long id, Integer accountId);
    List<Booking> findByStatusAndExpiresAtBefore(Booking.Status status, LocalDateTime expiresAt);
    List<Booking> findByAccountIdOrderByCreatedAtDesc(Integer accountId);

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

    @Query("""
            SELECT DISTINCT s.movie.id
            FROM Booking b
            JOIN Showtime s ON s.id = b.showtimeId
            WHERE b.accountId = :accountId
              AND b.status = :paidStatus
            """)
    List<Integer> findPaidMovieIdsByAccount(@Param("accountId") Integer accountId,
                                            @Param("paidStatus") Booking.Status paidStatus);

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

    @Query("""
            SELECT s.movie.id, COUNT(b)
            FROM Booking b
            JOIN Showtime s ON s.id = b.showtimeId
            WHERE b.status = :paidStatus
            GROUP BY s.movie.id
            """)
    List<Object[]> countPaidBookingsByMovie(@Param("paidStatus") Booking.Status paidStatus);
}
