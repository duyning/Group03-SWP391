package com.group3.cinema.service;

import com.group3.cinema.entity.Ticket;
import com.group3.cinema.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service xử lý logic nghiệp vụ liên quan đến vé xem phim (Ticket).
 *
 * Ngày thực hiện: 26/06/2026
 */
@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    /**
     * Lấy danh sách tất cả vé của một tài khoản, sắp xếp theo thời gian đặt mới nhất.
     *
     * @param accountId ID tài khoản người dùng
     * @return danh sách vé
     */
    public List<Ticket> getTicketsByAccount(int accountId) {
        return ticketRepository.findByAccountAccountIDOrderByBookingTimeDesc(accountId);
    }

    /**
     * Lấy chi tiết một vé cụ thể, đảm bảo vé thuộc về tài khoản đang đăng nhập.
     *
     * @param ticketId  ID của vé
     * @param accountId ID tài khoản người dùng
     * @return Optional chứa vé nếu tìm thấy và thuộc về user
     */
    public Optional<Ticket> getTicketDetail(Long ticketId, int accountId) {
        return ticketRepository.findByIdAndAccountAccountID(ticketId, accountId);
    }
}
