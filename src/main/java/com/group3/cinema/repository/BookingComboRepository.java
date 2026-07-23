/**
 * Interface Repository thao tác dữ liệu với các gói Combo thuộc đơn đặt vé (`booking_combos`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CustomerBookingService` và `TicketManagementService` để tìm kiếm và dọn dẹp combo trong đơn hàng.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.BookingCombo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingComboRepository extends JpaRepository<BookingCombo, Long> {

    /**
     * Lấy danh sách chi tiết các combo bắp nước đã chọn theo ID đơn đặt vé.
     */
    List<BookingCombo> findByBookingId(Long bookingId);

    /**
     * Xóa toàn bộ chi tiết combo liên quan tới ID đơn đặt vé (dùng khi hủy hoặc tạo lại đơn hàng).
     */
    void deleteByBookingId(Long bookingId);
}

