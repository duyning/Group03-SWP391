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
    long countByAccountIdAndVoucherCodeAndStatusIn(Integer accountId, String voucherCode, List<Booking.Status> statuses);
    List<Booking> findByAccountIdOrderByCreatedAtDesc(Integer accountId);

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
