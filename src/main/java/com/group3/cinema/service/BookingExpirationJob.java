/**
 * Tiến trình chạy tự động định kỳ (Background Job) giải phóng ghế giữ chỗ cho các đơn hàng quá hạn thanh toán (`BookingExpirationJob`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được Spring Scheduled kích hoạt mỗi 60 giây (`@Scheduled(fixedDelay = 60000)`).
 * - Gọi đến:
 *   + `BookingRepository`: Tìm kiếm các đơn hàng ở trạng thái `PENDING` có `expiresAt` nhỏ hơn thời điểm hiện tại và chuyển sang `EXPIRED`.
 *   + `BookingTicketRepository`: Xóa bỏ các vé tam giữ ghế (`deleteByBookingId`) để trả ghế trống lại hệ thống cho người khác đặt.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (26/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BookingExpirationJob {
    private final BookingRepository bookingRepository;
    private final BookingTicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public BookingExpirationJob(BookingRepository bookingRepository,
                                BookingTicketRepository ticketRepository,
                                PaymentRepository paymentRepository,
                                PaymentService paymentService) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    /**
     * Tự động quét và giải phóng đơn giữ chỗ hết hạn thanh toán mỗi phút một lần.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredBookings() {
        bookingRepository.findByStatusAndExpiresAtBefore(Booking.Status.PENDING, LocalDateTime.now())
                .forEach(booking -> {
                    Payment payment = paymentRepository
                            .findTopByBookingIdOrderByCreatedAtDesc(booking.getId())
                            .orElse(null);
                    if (payment != null && payment.getStatus() == Payment.Status.SUCCESS) {
                        // Không giải phóng ghế của giao dịch đã ghi nhận thanh toán.
                        return;
                    }
                    if (payment != null
                            && payment.getStatus() == Payment.Status.PENDING
                            && payment.getPaymentMethod() == Payment.Method.PAYOS
                            && payment.getOrderCode() != null
                            && !payment.getOrderCode().isBlank()) {
                        try {
                            // Đối soát trước khi xóa ghế. Phương thức này tự hoàn tất vé hoặc tự hết hạn
                            // nếu payOS xác nhận giao dịch vẫn chưa thanh toán.
                            paymentService.reconcilePayOsPayment(payment.getOrderCode());
                        } catch (IllegalArgumentException ex) {
                            // payOS tạm thời không phản hồi: giữ ghế và thử lại ở chu kỳ kế tiếp,
                            // tránh xóa dữ liệu của một giao dịch thực tế đã thanh toán.
                        }
                        return;
                    }
                    booking.setStatus(Booking.Status.EXPIRED);
                    ticketRepository.deleteByBookingId(booking.getId());
                    bookingRepository.save(booking);
                });
    }
}

