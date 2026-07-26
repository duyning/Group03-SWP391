/**
 * Repository thao tác bảng `payments`.
 *
 * Vai trò trong bán vé tại quầy:
 * - `CounterSaleService.completeSale(...)` lưu giao dịch CASH thành công ngay tại quầy.
 * - `CounterSaleService.createCounterPayment(...)` lưu giao dịch PAYOS ở trạng thái PENDING
 *   trước khi chuyển nhân viên/khách sang trang thanh toán payOS.
 * - Flow callback/return của payment gateway tìm payment theo `orderCode` để cập nhật kết quả.
 * - Màn quản lý hóa đơn/báo cáo tìm payment theo bookingId để hiển thị phương thức thanh toán và đối soát.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Tìm giao dịch theo mã thanh toán.
     *
     * Với payOS, `orderCode` là mã gửi sang gateway.
     * Với tiền mặt tại quầy, `orderCode` dạng POS... là mã đối soát/hóa đơn nội bộ.
     */
    Optional<Payment> findByOrderCode(String orderCode);

    /** Lấy giao dịch mới nhất của một booking để màn hóa đơn biết trạng thái/phương thức hiện tại. */
    Optional<Payment> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    /** Lấy payment theo nhiều booking để dashboard/invoice list tránh query từng dòng. */
    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);

    /** Lấy lịch sử các lần tạo/thử thanh toán của một booking, mới nhất trước. */
    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}

