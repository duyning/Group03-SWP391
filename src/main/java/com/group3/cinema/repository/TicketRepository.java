package com.group3.cinema.repository;

import com.group3.cinema.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByShowtimeIdAndDeletedFalse(Long showtimeId);

    Optional<Ticket> findByShowtimeIdAndSeatIdAndDeletedFalse(Long showtimeId, Long seatId);

    boolean existsByShowtimeIdAndStatusAndDeletedFalse(Long showtimeId, String status);

    long countByShowtimeIdAndDeletedFalse(Long showtimeId);

    long countByShowtimeIdAndStatusAndDeletedFalse(Long showtimeId, String status);

    @Query("SELECT COALESCE(SUM(t.finalPrice), 0) FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status = 'BOOKED' AND t.deleted = false")
    Double calculateRevenueByShowtimeId(@Param("showtimeId") Long showtimeId);

    @Query("SELECT t FROM Ticket t WHERE t.deleted = false AND " +
           "(:movieId IS NULL OR t.showtime.movie.id = :movieId) AND " +
           "(:room IS NULL OR t.showtime.room = :room) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:fromDate IS NULL OR t.showtime.showDate >= :fromDate) AND " +
           "(:toDate IS NULL OR t.showtime.showDate <= :toDate) AND " +
           "(:searchTerm IS NULL OR " +
           " CAST(t.id AS string) LIKE %:searchTerm% OR " +
           " t.seatNumber LIKE %:searchTerm% OR " +
           " t.customerPhone LIKE %:searchTerm% OR " +
           " t.customerName LIKE %:searchTerm%) " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<Ticket> searchTickets(@Param("movieId") Integer movieId,
                               @Param("room") String room,
                               @Param("status") String status,
                               @Param("fromDate") java.time.LocalDate fromDate,
                               @Param("toDate") java.time.LocalDate toDate,
                               @Param("searchTerm") String searchTerm);

    @Modifying
    @Transactional
    @Query("DELETE FROM Ticket t WHERE t.showtime.id = :showtimeId")
    void deleteAllByShowtimeId(@Param("showtimeId") Long showtimeId);
}
