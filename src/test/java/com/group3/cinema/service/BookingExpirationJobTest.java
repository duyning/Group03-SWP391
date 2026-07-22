package com.group3.cinema.service;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationJobTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingTicketRepository bookingTicketRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    private BookingExpirationJob job;

    @BeforeEach
    void setUp() {
        job = new BookingExpirationJob(
                bookingRepository, bookingTicketRepository, paymentRepository, paymentService);
    }

    @Test
    void reconcilesPendingPayOsPaymentBeforeDeletingHeldSeats() {
        Booking booking = expiredPendingBooking(21L);
        Payment payment = pendingPayOsPayment(21L, "178473363663");
        Payment reconciled = pendingPayOsPayment(21L, "178473363663");
        reconciled.setStatus(Payment.Status.SUCCESS);

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                org.mockito.ArgumentMatchers.eq(Booking.Status.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(21L))
                .thenReturn(Optional.of(payment));
        when(paymentService.reconcilePayOsPayment("178473363663")).thenReturn(reconciled);

        job.releaseExpiredBookings();

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.PENDING);
        verify(paymentService).reconcilePayOsPayment("178473363663");
        verify(bookingTicketRepository, never()).deleteByBookingId(21L);
    }

    @Test
    void payOsLookupFailureDefersSeatDeletionUntilNextRun() {
        Booking booking = expiredPendingBooking(22L);
        Payment payment = pendingPayOsPayment(22L, "178473363664");

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                org.mockito.ArgumentMatchers.eq(Booking.Status.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(22L))
                .thenReturn(Optional.of(payment));
        when(paymentService.reconcilePayOsPayment("178473363664"))
                .thenThrow(new IllegalArgumentException("payOS tạm thời không phản hồi."));

        job.releaseExpiredBookings();

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.PENDING);
        verify(bookingTicketRepository, never()).deleteByBookingId(22L);
    }

    @Test
    void expiresBookingThatHasNoPendingPayOsPayment() {
        Booking booking = expiredPendingBooking(23L);

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                org.mockito.ArgumentMatchers.eq(Booking.Status.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(23L))
                .thenReturn(Optional.empty());

        job.releaseExpiredBookings();

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.EXPIRED);
        verify(bookingTicketRepository).deleteByBookingId(23L);
        verify(bookingRepository).save(booking);
    }

    private Booking expiredPendingBooking(Long id) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setStatus(Booking.Status.PENDING);
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return booking;
    }

    private Payment pendingPayOsPayment(Long bookingId, String orderCode) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setOrderCode(orderCode);
        payment.setPaymentMethod(Payment.Method.PAYOS);
        payment.setStatus(Payment.Status.PENDING);
        return payment;
    }
}
