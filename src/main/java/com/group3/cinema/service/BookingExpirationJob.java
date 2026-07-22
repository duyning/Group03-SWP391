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

    /**
     * Tự động quét và giải phóng đơn giữ chỗ hết hạn thanh toán mỗi phút một lần.
     */
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

