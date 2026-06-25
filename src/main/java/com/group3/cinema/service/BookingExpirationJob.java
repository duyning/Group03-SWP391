package com.group3.cinema.service;

/*
 * Added on 2026-06-26: Periodically expires unpaid bookings and releases held seats.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BookingExpirationJob {
    private final BookingRepository bookingRepository;
    private final BookingTicketRepository ticketRepository;

    public BookingExpirationJob(BookingRepository bookingRepository,
                                BookingTicketRepository ticketRepository) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredBookings() {
        bookingRepository.findByStatusAndExpiresAtBefore(Booking.Status.PENDING, LocalDateTime.now())
                .forEach(booking -> {
                    booking.setStatus(Booking.Status.EXPIRED);
                    ticketRepository.deleteByBookingId(booking.getId());
                    bookingRepository.save(booking);
                });
    }
}
