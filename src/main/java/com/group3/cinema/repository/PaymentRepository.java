/**
 * Interface Repository thao tác dữ liệu nhật ký giao dịch thanh toán (`payments`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PayOSPaymentService`, `CustomerBookingService`, `TicketManagementService`.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Tìm thông tin giao dịch thanh toán theo mã đơn giao dịch (`orderCode`) gửi cổng thanh toán.
     */
    Optional<Payment> findByOrderCode(String orderCode);

    /**
     * Lấy giao dịch thanh toán mới nhất của một đơn đặt vé (`bookingId`).
     */
    Optional<Payment> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    /**
     * Lấy tất cả các giao dịch thanh toán thuộc danh sách các đơn đặt vé.
     */
    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);

    /**
     * Lấy danh sách lịch sử tất cả lần thanh toán (thành công/thất bại) của một đơn đặt vé.
     */
    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}

