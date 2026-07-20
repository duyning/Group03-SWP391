package com.group3.cinema.repository;

/*
 * Repository thao tác với bảng tickets.
 * Created/updated by: NinhDD - HE186113, TrienLX
 */

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

    List<Ticket> findByAccountAccountIDOrderByBookingTimeDesc(int accountId);

    Optional<Ticket> findByIdAndAccountAccountID(Long id, int accountId);

    List<Ticket> findByShowtimeId(Long showtimeId);

    List<Ticket> findByShowtimeIdAndDeletedFalse(Long showtimeId);

    Optional<Ticket> findByShowtimeIdAndSeatIdAndDeletedFalse(Long showtimeId, Long seatId);

    boolean existsByShowtimeIdAndStatus(Long showtimeId, String status);

    boolean existsByShowtimeIdAndStatusAndDeletedFalse(Long showtimeId, String status);

    long countByShowtimeId(Long showtimeId);

    long countByShowtimeIdAndDeletedFalse(Long showtimeId);

    long countByShowtimeIdAndStatus(Long showtimeId, String status);

    long countByShowtimeIdAndStatusAndDeletedFalse(Long showtimeId, String status);

    @Query("""
            SELECT COALESCE(SUM(t.finalPrice), 0)
            FROM Ticket t
            WHERE t.showtime.id = :showtimeId
              AND t.status = 'BOOKED'
              AND t.deleted = false
            """)
    Double calculateRevenueByShowtimeId(@Param("showtimeId") Long showtimeId);

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

    @Modifying
    @Transactional
    @Query("DELETE FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status = 'Còn trống'")
    void deleteUnsoldTicketsByShowtimeId(@Param("showtimeId") Long showtimeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Ticket t WHERE t.showtime.id = :showtimeId")
    void deleteAllByShowtimeId(@Param("showtimeId") Long showtimeId);

    @Query("SELECT t FROM Ticket t WHERE t.status <> 'Còn trống' AND t.deleted = false")
    List<Ticket> findAllBookedTickets();

    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.showtime.movie.id = :movieId AND t.status <> 'Còn trống' AND t.deleted = false")
    boolean hasBookedTicketsForMovie(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status <> 'Còn trống' AND t.deleted = false")
    boolean hasBookedTicketsForShowtime(@Param("showtimeId") Long showtimeId);
}
