package com.group3.cinema.repository;

import com.group3.cinema.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cung cấp các phương thức thao tác dữ liệu với bảng tickets.
 * Hỗ trợ truy vấn vé theo tài khoản người dùng.
 *
 * Ngày thực hiện: 26/06/2026
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Lấy danh sách vé của một tài khoản, sắp xếp theo thời gian đặt mới nhất.
     */
    List<Ticket> findByAccountAccountIDOrderByBookingTimeDesc(int accountId);

    /**
     * Lấy chi tiết một vé, đảm bảo vé thuộc về tài khoản đang đăng nhập (bảo mật).
     */
    Optional<Ticket> findByIdAndAccountAccountID(Long id, int accountId);
}
