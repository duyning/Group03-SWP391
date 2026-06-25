package com.group3.cinema.repository;

/*
 * Added on 2026-06-24: Repository for booking combo line items.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.BookingCombo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BookingComboRepository extends JpaRepository<BookingCombo, Long> {
    List<BookingCombo> findByBookingId(Long bookingId);
    void deleteByBookingId(Long bookingId);
}
