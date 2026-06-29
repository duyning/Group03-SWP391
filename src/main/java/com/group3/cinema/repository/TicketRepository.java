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

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByAccountAccountIDOrderByBookingTimeDesc(int accountId);

    Optional<Ticket> findByIdAndAccountAccountID(Long id, int accountId);

    List<Ticket> findByShowtimeId(Long showtimeId);

    long countByShowtimeId(Long showtimeId);

    long countByShowtimeIdAndStatus(Long showtimeId, String status);

    @Query("SELECT SUM(t.price) FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status = 'Đã bán'")
    Double calculateRevenueByShowtimeId(@Param("showtimeId") Long showtimeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Ticket t WHERE t.showtime.id = :showtimeId AND t.status = 'Còn trống'")
    void deleteUnsoldTicketsByShowtimeId(@Param("showtimeId") Long showtimeId);

    boolean existsByShowtimeIdAndStatus(Long showtimeId, String status);
}
