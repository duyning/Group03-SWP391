package com.group3.cinema.service;

import com.group3.cinema.dto.BookingHistoryDto;
import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.PaymentRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.WishlistRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import com.group3.cinema.service.payment.PaymentGatewayRouter;
import com.group3.cinema.service.payment.PaymentGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceBookingHistoryTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingTicketRepository bookingTicketRepository;
    @Mock private BookingEmailService bookingEmailService;
    @Mock private TicketRepository ticketRepository;
    @Mock private ShowtimeRepository showtimeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private WishlistRepository wishlistRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private PaymentGatewayRouter gatewayRouter;
    @Mock private PaymentGatewayService payOsGateway;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, bookingRepository, bookingTicketRepository,
                bookingEmailService, ticketRepository, showtimeRepository, accountRepository,
                voucherRepository, wishlistRepository, loyaltyService, gatewayRouter);
    }

    @Test
    void expiredPendingTransactionIsNotShownAsProcessingWithoutTicketInfo() {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", 10L);
        booking.setAccountId(7);
        booking.setShowtimeId(12L);
        booking.setStatus(Booking.Status.PENDING);
        booking.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        booking.setTotalAmount(new BigDecimal("60000"));

        Payment payment = new Payment();
        payment.setBookingId(10L);
        payment.setOrderCode("178473274635");
        payment.setPaymentMethod(Payment.Method.PAYOS);
        payment.setStatus(Payment.Status.PENDING);
        payment.setAmount(new BigDecimal("60000"));

        Movie movie = new Movie();
        movie.setTitle("Phim thử nghiệm");
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);

        when(bookingRepository.findByAccountIdOrderByCreatedAtDesc(7)).thenReturn(List.of(booking));
        when(bookingTicketRepository.findByBookingId(10L)).thenReturn(List.of());
        when(paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderCode("178473274635")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(gatewayRouter.gateway(Payment.Method.PAYOS)).thenReturn(payOsGateway);
        when(payOsGateway.queryPayment("178473274635")).thenReturn(
                new PaymentGatewayService.GatewayPaymentStatus(
                        "178473274635", new BigDecimal("60000"), false,
                        "PENDING", "link-10", ""));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(showtimeRepository.findById(12L)).thenReturn(Optional.of(showtime));

        List<BookingHistoryDto> history = service.getBookingHistory(7);

        assertThat(history).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo("Thất bại");
            assertThat(item.getSummary()).contains("không hoàn tất").doesNotContain("Không có thông tin vé");
        });
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
        assertThat(booking.getStatus()).isEqualTo(Booking.Status.EXPIRED);
        verify(bookingTicketRepository).deleteByBookingId(10L);
    }

    @Test
    void paidPayOsStatusStillIssuesTicketWhenLocalDeadlinePassedButHeldSeatRemains() {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", 11L);
        booking.setAccountId(7);
        booking.setShowtimeId(12L);
        booking.setStatus(Booking.Status.PENDING);
        booking.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        booking.setTotalAmount(new BigDecimal("75000"));

        Payment payment = new Payment();
        payment.setBookingId(11L);
        payment.setOrderCode("178473363663");
        payment.setPaymentMethod(Payment.Method.PAYOS);
        payment.setStatus(Payment.Status.PENDING);
        payment.setAmount(new BigDecimal("75000"));

        BookingTicket heldSeat = new BookingTicket();
        heldSeat.setBookingId(11L);
        heldSeat.setShowtimeId(12L);
        heldSeat.setSeatId(6L);
        heldSeat.setSeatLabel("C6");
        heldSeat.setSeatType("STANDARD");
        heldSeat.setPrice(new BigDecimal("75000"));
        heldSeat.setStatus(BookingTicket.Status.HOLDING);
        heldSeat.setHoldToken("hold-11");
        heldSeat.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));

        Account account = new Account();
        account.setAccountID(7);
        Movie movie = new Movie();
        movie.setTitle("Phim Điện Ảnh Doraemon");
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom("Phòng 1");
        showtime.setShowDate(LocalDate.now().plusDays(1));
        showtime.setShowTime(LocalTime.of(19, 30));

        when(paymentRepository.findByOrderCode("178473363663")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(booking));
        when(gatewayRouter.gateway(Payment.Method.PAYOS)).thenReturn(payOsGateway);
        when(payOsGateway.queryPayment("178473363663")).thenReturn(
                new PaymentGatewayService.GatewayPaymentStatus(
                        "178473363663", new BigDecimal("75000"), true,
                        "00", "payos-transaction-11", "Thanh toán thành công."));
        when(bookingTicketRepository.findByBookingId(11L)).thenReturn(List.of(heldSeat));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(7)).thenReturn(Optional.of(account));
        when(showtimeRepository.findById(12L)).thenReturn(Optional.of(showtime));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment reconciled = service.reconcilePayOsPayment("178473363663");

        assertThat(reconciled.getStatus()).isEqualTo(Payment.Status.SUCCESS);
        assertThat(booking.getStatus()).isEqualTo(Booking.Status.PAID);
        assertThat(heldSeat.getStatus()).isEqualTo(BookingTicket.Status.BOOKED);
        assertThat(heldSeat.getHoldToken()).isNull();
        assertThat(heldSeat.getHoldExpiresAt()).isNull();
        verify(ticketRepository).save(any(Ticket.class));
        verify(bookingEmailService).sendTicketEmail(11L);
    }

    @Test
    void payOsLookupFailureKeepsTransactionPendingForLaterReconciliation() {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", 12L);
        booking.setAccountId(7);
        booking.setShowtimeId(13L);
        booking.setStatus(Booking.Status.PENDING);
        booking.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(2));
        booking.setTotalAmount(new BigDecimal("75000"));

        Payment payment = new Payment();
        payment.setBookingId(12L);
        payment.setOrderCode("178473363664");
        payment.setPaymentMethod(Payment.Method.PAYOS);
        payment.setStatus(Payment.Status.PENDING);
        payment.setAmount(new BigDecimal("75000"));

        Showtime showtime = new Showtime();
        Movie movie = new Movie();
        movie.setTitle("Phim thử nghiệm");
        showtime.setMovie(movie);

        when(bookingRepository.findByAccountIdOrderByCreatedAtDesc(7)).thenReturn(List.of(booking));
        when(bookingTicketRepository.findByBookingId(12L)).thenReturn(List.of());
        when(paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(12L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderCode("178473363664")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(booking));
        when(gatewayRouter.gateway(Payment.Method.PAYOS)).thenReturn(payOsGateway);
        when(payOsGateway.queryPayment("178473363664"))
                .thenThrow(new IllegalArgumentException("payOS tạm thời không phản hồi."));
        when(showtimeRepository.findById(13L)).thenReturn(Optional.of(showtime));

        List<BookingHistoryDto> history = service.getBookingHistory(7);

        assertThat(history).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo("Đang xử lý");
            assertThat(item.getSummary()).contains("đang chờ payOS xác nhận");
        });
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(booking.getStatus()).isEqualTo(Booking.Status.PENDING);
        verify(bookingTicketRepository, never()).deleteByBookingId(12L);
    }
}
