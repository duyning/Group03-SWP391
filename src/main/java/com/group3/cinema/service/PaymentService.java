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
    private final LoyaltyService loyaltyService;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          BookingTicketRepository ticketRepository,
                          BookingEmailService bookingEmailService,
                          TicketRepository realTicketRepository,
                          ShowtimeRepository showtimeRepository,
                          AccountRepository accountRepository,
                          VoucherRepository voucherRepository,
                          WishlistRepository wishlistRepository,
                          LoyaltyService loyaltyService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.bookingEmailService = bookingEmailService;
        this.realTicketRepository = realTicketRepository;
        this.showtimeRepository = showtimeRepository;
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.wishlistRepository = wishlistRepository;
        this.loyaltyService = loyaltyService;
    }


    @Transactional
    public Payment createPayment(Long bookingId, Integer accountId, String method) {
        /*
         * Khóa nghiệp vụ trước khi tạo giao dịch: đơn phải thuộc tài khoản, còn hạn,
         * đang PENDING và voucher vẫn còn lượt. Giao dịch PENDING cũ được tái sử dụng
         * để thao tác double-click không tạo nhiều orderCode.
         */
        if (method == null || !Payment.Method.PAYOS.name().equalsIgnoreCase(method.trim())) {
            throw new IllegalArgumentException("Chỉ hỗ trợ thanh toán qua payOS.");
        }

        Booking booking = requirePayableBooking(bookingId, accountId);
        ensureVoucherStillAvailable(booking);
        Payment existingPending = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .filter(payment -> payment.getStatus() == Payment.Status.PENDING)
                .orElse(null);
        if (existingPending != null) {
            return existingPending;
        }

        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setPaymentMethod(Payment.Method.PAYOS);
        payment.setOrderCode(generatePayOsOrderCode());
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(Payment.Status.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment processGatewayResult(String orderCode, boolean success, String responseCode,
                                        String transactionId, String message) {
        /*
         * Đây là điểm hội tụ kết quả từ payOS. Chỉ giao dịch PENDING mới được
         * chuyển trạng thái, giúp callback return và webhook gọi lặp vẫn an toàn.
         * Thành công sẽ chốt voucher, đổi ghế HOLDING thành BOOKED và phát hành Ticket.
         */
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
        // Việc kiểm tra quyền sở hữu được thực hiện cùng truy vấn để không lộ booking của tài khoản khác.
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
        // Câu lệnh increment có điều kiện ngăn hai giao dịch cuối cùng cùng dùng một lượt voucher.
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

    private String generatePayOsOrderCode() {
        long epochSeconds = System.currentTimeMillis() / 1000;
        int suffix = ThreadLocalRandom.current().nextInt(10, 99);
        return String.valueOf(epochSeconds * 100 + suffix);
    }

    private void sendEmailIfPaid(Payment payment) {
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            bookingEmailService.sendTicketEmail(payment.getBookingId());
        }
    }

    private void saveRealTickets(Booking booking, List<BookingTicket> bookingTickets, Payment payment) {
        /*
         * booking_tickets quản lý khóa/giữ ghế, còn tickets là dữ liệu vé điện tử
         * hiển thị ở "Vé của tôi". Sau khi sao chép thành công, hệ thống cộng điểm thành viên.
         */
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
                    t.setPaymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "PAYOS");
                    t.setBookingCode(payment.getOrderCode() != null ? payment.getOrderCode() : "CF-" + booking.getId());
                    realTicketRepository.save(t);
                }
                
                // Điểm thành viên chỉ được cộng sau khi đã phát hành đủ vé của giao dịch thành công.
                loyaltyService.addLoyaltyPoints(booking.getAccountId(), booking.getTotalAmount());
            }
        } catch (Exception ex) {
            System.err.println("Warning: Failed to copy tickets to customer account display: " + ex.getMessage());
        }
    }

    public void cleanWishlistIfFromWishlist(jakarta.servlet.http.HttpSession session, Payment payment) {
        if (session == null || payment == null) {
            return;
        }
        try {
            bookingRepository.findById(payment.getBookingId()).ifPresent(booking -> {
                showtimeRepository.findById(booking.getShowtimeId()).ifPresent(showtime -> {
                    var movie = showtime.getMovie();
                    if (movie != null) {
                        String attrName = "from_wishlist_movie_" + movie.getId();
                        Boolean fromWishlist = (Boolean) session.getAttribute(attrName);
                        if (fromWishlist != null && fromWishlist) {
                            wishlistRepository.findByAccountAccountIDAndMovieId(booking.getAccountId(), movie.getId())
                                    .ifPresent(item -> {
                                        wishlistRepository.delete(item);
                                        System.out.println("Success: Auto-removed movie " + movie.getTitle() + " from wishlist for account " + booking.getAccountId());
                                    });
                            session.removeAttribute(attrName);
                        }
                    }
                });
            });
        } catch (Exception ex) {
            System.err.println("Warning: Failed to clean wishlist for checkout: " + ex.getMessage());
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
