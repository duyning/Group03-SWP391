package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Payment lifecycle for customer ticket booking.
 * Updated on 2026-06-26: Successful payments trigger booking confirmation email.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.PaymentRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.WishlistRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.group3.cinema.dto.BookingHistoryDto;
import com.group3.cinema.entity.Showtime;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingTicketRepository ticketRepository;
    private final BookingEmailService bookingEmailService;
    private final TicketRepository realTicketRepository;
    private final ShowtimeRepository showtimeRepository;
    private final AccountRepository accountRepository;
    private final VoucherRepository voucherRepository;
    private final WishlistRepository wishlistRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          BookingTicketRepository ticketRepository,
                          BookingEmailService bookingEmailService,
                          TicketRepository realTicketRepository,
                          ShowtimeRepository showtimeRepository,
                          AccountRepository accountRepository,
                          VoucherRepository voucherRepository,
                          WishlistRepository wishlistRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.bookingEmailService = bookingEmailService;
        this.realTicketRepository = realTicketRepository;
        this.showtimeRepository = showtimeRepository;
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.wishlistRepository = wishlistRepository;
    }


    @Transactional
    public Payment createPayment(Long bookingId, Integer accountId, String method) {
        Booking booking = requirePayableBooking(bookingId, accountId);
        ensureVoucherStillAvailable(booking);
        Payment existingPending = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .filter(payment -> payment.getStatus() == Payment.Status.PENDING)
                .orElse(null);
        if (existingPending != null) {
            return existingPending;
        }

        Payment.Method paymentMethod;
        try {
            paymentMethod = Payment.Method.valueOf(method.toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        }

        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setPaymentMethod(paymentMethod);
        payment.setOrderCode(generateOrderCode(paymentMethod));
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(Payment.Status.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment processResult(String orderCode, Integer accountId, String result) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán."));
        Booking booking = bookingRepository.findByIdAndAccountId(payment.getBookingId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Giao dịch không thuộc tài khoản này."));
        if (payment.getStatus() != Payment.Status.PENDING) {
            return payment;
        }

        String normalized = result == null ? "FAILED" : result.toUpperCase();
        if ("SUCCESS".equals(normalized)) {
            if (booking.getStatus() != Booking.Status.PENDING
                    || booking.getExpiresAt().isBefore(LocalDateTime.now())) {
                expireBooking(booking);
                payment.setStatus(Payment.Status.FAILED);
                payment.setResponseCode("EXPIRED");
                payment.setErrorMessage("Đơn đặt vé đã hết hạn trước khi thanh toán hoàn tất.");
                return paymentRepository.save(payment);
            }
            markVoucherAsUsed(booking);
            payment.setStatus(Payment.Status.SUCCESS);
            payment.setResponseCode("00");
            payment.setTransactionId(UUID.randomUUID().toString());
            payment.setPaidAt(LocalDateTime.now());
            booking.setStatus(Booking.Status.PAID);
            booking.setPaidAt(LocalDateTime.now());
            List<BookingTicket> tickets = ticketRepository.findByBookingId(booking.getId());
            tickets.forEach(ticket -> {
                ticket.setStatus(BookingTicket.Status.BOOKED);
                ticket.setHoldToken(null);
                ticket.setHoldExpiresAt(null);
            });
            ticketRepository.saveAll(tickets);
            saveRealTickets(booking, tickets, payment);
        } else if ("CANCELLED".equals(normalized)) {
            payment.setStatus(Payment.Status.CANCELLED);
            payment.setResponseCode("24");
            cancelBookingAndReleaseSeats(booking);
            payment.setErrorMessage("Khách hàng hủy thanh toán.");
        } else {
            payment.setStatus(Payment.Status.FAILED);
            payment.setResponseCode("99");
            payment.setErrorMessage("Giao dịch không thành công.");
            booking.setStatus(Booking.Status.CANCELLED);
            ticketRepository.deleteByBookingId(booking.getId());
        }

        bookingRepository.save(booking);
        Payment savedPayment = paymentRepository.save(payment);
        sendEmailIfPaid(savedPayment);
        return savedPayment;
    }

    @Transactional
    public Payment processGatewayResult(String orderCode, boolean success, String responseCode,
                                        String transactionId, String message) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán."));
        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        if (payment.getStatus() != Payment.Status.PENDING) {
            return payment;
        }

        if (success) {
            if (booking.getStatus() != Booking.Status.PENDING
                    || booking.getExpiresAt().isBefore(LocalDateTime.now())) {
                expireBooking(booking);
                payment.setStatus(Payment.Status.FAILED);
                payment.setResponseCode("EXPIRED");
                payment.setErrorMessage("Đơn đặt vé đã hết hạn trước khi thanh toán hoàn tất.");
                return paymentRepository.save(payment);
            }
            markVoucherAsUsed(booking);
            payment.setStatus(Payment.Status.SUCCESS);
            payment.setResponseCode(responseCode);
            payment.setTransactionId(transactionId);
            payment.setPaidAt(LocalDateTime.now());
            booking.setStatus(Booking.Status.PAID);
            booking.setPaidAt(LocalDateTime.now());
            List<BookingTicket> tickets = ticketRepository.findByBookingId(booking.getId());
            tickets.forEach(ticket -> {
                ticket.setStatus(BookingTicket.Status.BOOKED);
                ticket.setHoldToken(null);
                ticket.setHoldExpiresAt(null);
            });
            ticketRepository.saveAll(tickets);
            saveRealTickets(booking, tickets, payment);
        } else if ("CANCELLED".equalsIgnoreCase(responseCode)) {
            payment.setStatus(Payment.Status.CANCELLED);
            payment.setResponseCode(responseCode);
            cancelBookingAndReleaseSeats(booking);
            payment.setErrorMessage(message == null || message.isBlank() ? "Khách hàng hủy thanh toán." : message);
        } else if ("PENDING".equalsIgnoreCase(responseCode)) {
            payment.setResponseCode(responseCode);
            payment.setErrorMessage(message);
        } else {
            payment.setStatus(Payment.Status.FAILED);
            payment.setResponseCode(responseCode);
            payment.setErrorMessage(message == null || message.isBlank() ? "Giao dịch không thành công." : message);
            booking.setStatus(Booking.Status.CANCELLED);
            ticketRepository.deleteByBookingId(booking.getId());
        }
        bookingRepository.save(booking);
        Payment savedPayment = paymentRepository.save(payment);
        sendEmailIfPaid(savedPayment);
        return savedPayment;
    }

    @Transactional
    public Payment getPayment(String orderCode, Integer accountId) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));
        Booking booking = bookingRepository.findByIdAndAccountId(payment.getBookingId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền xem giao dịch này."));
        if (payment.getStatus() == Payment.Status.PENDING
                && booking.getStatus() == Booking.Status.PENDING
                && booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireBooking(booking);
            payment.setStatus(Payment.Status.FAILED);
            payment.setResponseCode("EXPIRED");
            payment.setErrorMessage("Đơn đặt vé đã hết hạn do quá thời gian thanh toán.");
            return paymentRepository.save(payment);
        }
        return payment;
    }

    @Transactional
    public Payment getPaymentPublic(String orderCode) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));
        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        if (payment.getStatus() == Payment.Status.PENDING
                && booking.getStatus() == Booking.Status.PENDING
                && booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireBooking(booking);
            payment.setStatus(Payment.Status.FAILED);
            payment.setResponseCode("EXPIRED");
            payment.setErrorMessage("Đơn đặt vé đã hết hạn do quá thời gian thanh toán.");
            return paymentRepository.save(payment);
        }
        return payment;
    }

    @Transactional
    public Booking requirePayableBooking(Long id, Integer accountId) {
        Booking booking = bookingRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        if (booking.getStatus() != Booking.Status.PENDING) {
            throw new IllegalArgumentException("Đơn này không thể thanh toán.");
        }
        if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireBooking(booking);
            throw new IllegalArgumentException("Đơn đặt vé đã hết hạn.");
        }
        return booking;
    }

    private void expireBooking(Booking booking) {
        booking.setStatus(Booking.Status.EXPIRED);
        bookingRepository.save(booking);
        ticketRepository.deleteByBookingId(booking.getId());
    }

    private void cancelBookingAndReleaseSeats(Booking booking) {
        booking.setStatus(Booking.Status.CANCELLED);
        ticketRepository.deleteByBookingId(booking.getId());
    }

    private void ensureVoucherStillAvailable(Booking booking) {
        String voucherCode = normalizeVoucherCode(booking.getVoucherCode());
        if (voucherCode == null) {
            return;
        }
        voucherRepository.findByCodeIgnoreCase(voucherCode).ifPresent(voucher -> {
            int usedQuantity = voucher.getUsedQuantity() == null ? 0 : voucher.getUsedQuantity();
            Integer totalQuantity = voucher.getTotalQuantity();
            if (totalQuantity != null && usedQuantity >= totalQuantity) {
                throw new IllegalArgumentException("Voucher " + voucher.getCode()
                        + " đã hết số lượng phát hành. Vui lòng quay lại chọn voucher khác.");
            }
        });
    }

    private void markVoucherAsUsed(Booking booking) {
        String voucherCode = normalizeVoucherCode(booking.getVoucherCode());
        if (voucherCode == null) {
            return;
        }
        boolean managedVoucher = voucherRepository.findByCodeIgnoreCase(voucherCode).isPresent();
        if (!managedVoucher) {
            return;
        }
        int updatedRows = voucherRepository.incrementUsedQuantityIfAvailable(voucherCode);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Voucher " + voucherCode
                    + " đã hết số lượng phát hành. Vui lòng quay lại chọn voucher khác.");
        }
    }

    private String normalizeVoucherCode(String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return null;
        }
        return voucherCode.trim().toUpperCase();
    }

    private String generateOrderCode(Payment.Method method) {
        if (method == Payment.Method.PAYOS) {
            long epochSeconds = System.currentTimeMillis() / 1000;
            int suffix = ThreadLocalRandom.current().nextInt(10, 99);
            return String.valueOf(epochSeconds * 100 + suffix);
        }
        return "CF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void sendEmailIfPaid(Payment payment) {
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            bookingEmailService.sendTicketEmail(payment.getBookingId());
        }
    }

    private void saveRealTickets(Booking booking, List<BookingTicket> bookingTickets, Payment payment) {
        try {
            var accountOpt = accountRepository.findById(booking.getAccountId());
            var showtimeOpt = showtimeRepository.findById(booking.getShowtimeId());
            if (accountOpt.isPresent() && showtimeOpt.isPresent()) {
                var account = accountOpt.get();
                var showtime = showtimeOpt.get();
                var movie = showtime.getMovie();
                
                for (BookingTicket bt : bookingTickets) {
                    Ticket t = new Ticket();
                    t.setAccount(account);
                    t.setMovie(movie);
                    t.setRoomName(showtime.getRoom());
                    t.setSeatLabel(bt.getSeatLabel());
                    t.setSeatType(bt.getSeatType());
                    t.setShowDate(showtime.getShowDate());
                    t.setShowTime(showtime.getShowTime());
                    t.setPrice(bt.getPrice());
                    t.setBookingTime(booking.getCreatedAt());
                    t.setStatus("CONFIRMED");
                    t.setPaymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "Momo");
                    t.setBookingCode(payment.getOrderCode() != null ? payment.getOrderCode() : "CF-" + booking.getId());
                    realTicketRepository.save(t);
                }

                // Tự động xoá khỏi wishlist khi thanh toán/đặt mua thành công bộ phim này
                try {
                    wishlistRepository.findByAccountAccountIDAndMovieId(account.getAccountID(), movie.getId())
                            .ifPresent(wishlistRepository::delete);
                } catch (Exception ex) {
                    System.err.println("Warning: Failed to auto-remove movie from wishlist: " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            System.err.println("Warning: Failed to copy tickets to customer account display: " + ex.getMessage());
        }
    }
    public List<BookingHistoryDto> getBookingHistory(Integer accountId) {
        List<Booking> bookings = bookingRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        List<BookingHistoryDto> dtos = new ArrayList<>();
        
        for (Booking booking : bookings) {
            BookingHistoryDto dto = new BookingHistoryDto();
            dto.setBookingTime(booking.getCreatedAt());
            dto.setTotalAmount(booking.getTotalAmount());
            
            Payment payment = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(booking.getId()).orElse(null);
            
            if (payment != null) {
                dto.setBookingCode(payment.getOrderCode() != null ? payment.getOrderCode() : "CF-" + booking.getId());
                dto.setPaymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "Thẻ/Ví");
                
                switch (payment.getStatus()) {
                    case SUCCESS:
                        dto.setStatus("Thành công");
                        dto.setStatusClass("status-success");
                        break;
                    case FAILED:
                        dto.setStatus("Thất bại");
                        dto.setStatusClass("status-failed");
                        break;
                    case CANCELLED:
                        dto.setStatus("Đã hủy");
                        dto.setStatusClass("status-cancelled");
                        break;
                    case PENDING:
                    default:
                        dto.setStatus("Đang xử lý");
                        dto.setStatusClass("status-pending");
                        break;
                }
            } else {
                dto.setBookingCode("CF-" + booking.getId());
                dto.setPaymentMethod("Chưa chọn");
                
                switch (booking.getStatus()) {
                    case CANCELLED:
                    case EXPIRED:
                        dto.setStatus("Đã hủy");
                        dto.setStatusClass("status-cancelled");
                        break;
                    case PENDING:
                    default:
                        dto.setStatus("Đang chờ thanh toán");
                        dto.setStatusClass("status-pending");
                        break;
                }
            }
            
            Showtime showtime = showtimeRepository.findById(booking.getShowtimeId()).orElse(null);
            String movieTitle = (showtime != null && showtime.getMovie() != null) ? showtime.getMovie().getTitle() : "Phim không xác định";
            List<BookingTicket> tickets = ticketRepository.findByBookingId(booking.getId());
            
            if (!tickets.isEmpty()) {
                String seats = tickets.stream().map(BookingTicket::getSeatLabel).collect(Collectors.joining(", "));
                dto.setSummary(String.format("Thanh toán %d vé xem phim \"%s\" (Ghế %s)", tickets.size(), movieTitle, seats));
            } else {
                dto.setSummary("Không có thông tin vé");
            }
            
            dtos.add(dto);
        }
        
        return dtos;
    }
}
