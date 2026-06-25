package com.group3.cinema.repository;

/*
 * Added on 2026-06-24: Repository for customer booking records.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdAndAccountId(Long id, Integer accountId);
    List<Booking> findByStatusAndExpiresAtBefore(Booking.Status status, LocalDateTime expiresAt);
}
