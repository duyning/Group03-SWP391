package com.group3.cinema.repository;

/*
 * Added on 2026-06-24: Repository for held and booked customer seats.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.BookingTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
public interface BookingTicketRepository extends JpaRepository<BookingTicket, Long> {
    List<BookingTicket> findByShowtimeId(Long showtimeId);
    List<BookingTicket> findByHoldToken(String holdToken);
    List<BookingTicket> findByBookingId(Long bookingId);
    List<BookingTicket> findByShowtimeIdAndSeatIdIn(Long showtimeId, Collection<Long> seatIds);
    int deleteByStatusAndHoldExpiresAtBefore(BookingTicket.Status status, LocalDateTime now);
    int deleteByBookingId(Long bookingId);
    @Modifying @Query("DELETE FROM BookingTicket t WHERE t.holdToken = :token AND t.bookingId IS NULL")
    int deleteUnbookedByHoldToken(@Param("token") String token);
}
