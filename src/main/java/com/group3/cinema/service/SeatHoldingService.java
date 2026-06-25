package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Seat map loading and temporary seat holding for customer booking.
 * Updated on 2026-06-26: Seat prices are loaded from SQL table booking_seat_prices.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.dto.BookingSeatView;
import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeatHoldingService {
    public static final int HOLD_MINUTES = 5;
    private static final Set<String> SELLABLE_TYPES = Set.of("std", "vip", "couple");
    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final BookingTicketRepository ticketRepository;
    private final JdbcTemplate jdbcTemplate;

    public SeatHoldingService(SeatRepository seatRepository, RoomRepository roomRepository,
                              BookingTicketRepository ticketRepository,
                              JdbcTemplate jdbcTemplate) {
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<BookingSeatView> getSeatMap(BookingSelection selection, String ownToken) {
        releaseExpired();
        Room room = roomRepository.findById(selection.roomId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng chiếu."));
        List<BookingTicket> tickets = ticketRepository.findByShowtimeId(selection.showtimeId());
        Map<Long, BookingTicket> states = tickets.stream()
                .collect(Collectors.toMap(BookingTicket::getSeatId, Function.identity()));
        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId()).stream()
                .map(seat -> toView(seat, states.get(seat.getId()), ownToken))
                .toList();
    }

    @Transactional
    public HoldResult holdSeats(BookingSelection selection, Collection<Long> requestedIds, String currentToken) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một ghế.");
        }
        LinkedHashSet<Long> seatIds = new LinkedHashSet<>(requestedIds);
        if (seatIds.size() > 8) {
            throw new IllegalArgumentException("Mỗi lần đặt tối đa 8 ghế.");
        }
        releaseExpired();
        String token = currentToken == null || currentToken.isBlank()
                ? UUID.randomUUID().toString() : currentToken;
        ticketRepository.deleteUnbookedByHoldToken(token);
        ticketRepository.flush();

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()
                || seats.stream().anyMatch(s -> !selection.roomId().equals(s.getRoomId())
                || !SELLABLE_TYPES.contains(normalizeType(s.getSeatType())))) {
            throw new IllegalArgumentException("Danh sách ghế không hợp lệ hoặc có ghế không thể bán.");
        }
        if (!ticketRepository.findByShowtimeIdAndSeatIdIn(selection.showtimeId(), seatIds).isEmpty()) {
            throw new IllegalArgumentException("Một hoặc nhiều ghế vừa được người khác chọn. Vui lòng chọn lại.");
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(HOLD_MINUTES);
        List<BookingTicket> holds = new ArrayList<>();
        for (Seat seat : seats) {
            BookingTicket ticket = new BookingTicket();
            ticket.setShowtimeId(selection.showtimeId());
            ticket.setSeatId(seat.getId());
            ticket.setSeatLabel(seat.getSeatLabel());
            ticket.setSeatType(normalizeType(seat.getSeatType()));
            ticket.setPrice(priceFor(seat.getSeatType()));
            ticket.setStatus(BookingTicket.Status.HOLDING);
            ticket.setHoldToken(token);
            ticket.setHoldExpiresAt(expiresAt);
            holds.add(ticket);
        }
        try {
            ticketRepository.saveAllAndFlush(holds);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Ghế vừa được người khác giữ. Vui lòng tải lại sơ đồ ghế.");
        }
        holds.sort(Comparator.comparing(BookingTicket::getSeatLabel));
        return new HoldResult(token, expiresAt, holds);
    }

    @Transactional
    public void releaseHold(String token) {
        if (token != null && !token.isBlank()) ticketRepository.deleteUnbookedByHoldToken(token);
    }

    @Transactional
    public void releaseExpired() {
        ticketRepository.deleteByStatusAndHoldExpiresAtBefore(BookingTicket.Status.HOLDING, LocalDateTime.now());
    }

    public BigDecimal priceFor(String type) {
        String normalizedType = normalizeType(type);
        List<BigDecimal> prices = jdbcTemplate.query(
                "SELECT price FROM booking_seat_prices WHERE seat_type = ? AND active = 1",
                (rs, rowNum) -> rs.getBigDecimal("price"),
                normalizedType
        );
        if (prices.isEmpty()) {
            throw new IllegalArgumentException("Chưa cấu hình giá ghế trong cơ sở dữ liệu: " + normalizedType);
        }
        return prices.get(0);
    }

    private BookingSeatView toView(Seat seat, BookingTicket ticket, String ownToken) {
        String type = normalizeType(seat.getSeatType());
        String status;
        if (!SELLABLE_TYPES.contains(type)) status = "UNAVAILABLE";
        else if (ticket == null) status = "AVAILABLE";
        else if (ticket.getStatus() == BookingTicket.Status.BOOKED) status = "BOOKED";
        else if (Objects.equals(ticket.getHoldToken(), ownToken)) status = "SELECTED";
        else status = "HOLDING";
        return new BookingSeatView(seat.getId(), seat.getRowIndex(), seat.getColIndex(),
                seat.getSeatLabel(), type, status, SELLABLE_TYPES.contains(type) ? priceFor(type) : BigDecimal.ZERO);
    }

    private String normalizeType(String type) { return type == null ? "std" : type.trim().toLowerCase(); }
    public record HoldResult(String token, LocalDateTime expiresAt, List<BookingTicket> tickets) { }
}
