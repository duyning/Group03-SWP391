/**
 * Service xử lý Giữ ghế tạm thời (Seat Holding) 5 phút cho Khách hàng chọn vé trên sơ đồ ghế (`SeatHoldingService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `BookingSeatApiController` khi khách hàng xem và chọn giữ ghế xem phim.
 * - Tương tác với:
 *   + `SeatRepository`: Tra cứu sơ đồ ghế thuộc phòng chiếu (`findByRoomIdOrderByRowIndexAscColIndexAsc`).
 *   + `BookingTicketRepository`: Tạo và xóa trạng thái giữ ghế `HOLDING` (`deleteUnbookedByHoldToken`, `saveAllAndFlush`), dọn dẹp các giữ ghế hết hạn 5 phút (`deleteByStatusAndHoldExpiresAtBefore`).
 *   + `SeatTypeRepository`: Lấy loại ghế (Standard, VIP, Sweetbox Couple) và bảng màu hiển thị (`findByActiveTrueOrderByIdAsc`).
 *   + `ShowtimeRepository`: Lấy suất chiếu.
 *   + `TicketService`: Đơn giá vé tương ứng từng loại ghế (`calculatePrice`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSeatView;
import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.SeatTypeRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeatHoldingService {
    /** Thời gian khóa giữ ghế tạm thời cho giao dịch mua vé (tính bằng phút). */
    public static final int HOLD_MINUTES = 5;

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final BookingTicketRepository ticketRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final ShowtimeRepository showtimeRepository;
    private final TicketService ticketService;

    public SeatHoldingService(SeatRepository seatRepository,
                              RoomRepository roomRepository,
                              BookingTicketRepository ticketRepository,
                              SeatTypeRepository seatTypeRepository,
                              ShowtimeRepository showtimeRepository,
                              TicketService ticketService) {
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.showtimeRepository = showtimeRepository;
        this.ticketService = ticketService;
    }

    /** Lấy danh sách các loại ghế đang mở hoạt động. */
    @Transactional(readOnly = true)
    public List<SeatType> getActiveSeatTypes() {
        return seatTypeRepository.findByActiveTrueOrderByIdAsc();
    }

    /**
     * Tải toàn bộ ma trận sơ đồ ghế phòng chiếu và ánh xạ trạng thái từng vị trí (AVAILABLE, HOLDING, BOOKED, SELECTED).
     * 
     * @param selection Đối tượng lựa chọn thông tin phòng và suất chiếu.
     * @param ownToken Token giữ ghế cá nhân hiện tại của người dùng.
     * @return Danh sách BookingSeatView định dạng UI.
     */
    @Transactional
    public List<BookingSeatView> getSeatMap(BookingSelection selection, String ownToken) {
        releaseExpired();
        Room room = roomRepository.findById(selection.roomId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng chiếu."));
        Showtime showtime = showtimeRepository.findById(selection.showtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));
        Map<String, SeatType> seatTypes = seatTypesByCode();
        Map<Long, BookingTicket> states = ticketRepository.findByShowtimeId(selection.showtimeId()).stream()
                .collect(Collectors.toMap(BookingTicket::getSeatId, Function.identity(), (first, ignored) -> first));

        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId()).stream()
                .filter(seat -> !"skip".equals(normalizeType(seat.getSeatType())))
                .map(seat -> toView(seat, states.get(seat.getId()), ownToken, seatTypes, showtime))
                .toList();
    }

    /**
     * Thực hiện tạm khóa (Giữ ghế) các vị trí khách chọn trong thời hạn 5 phút.
     * 
     * @param selection Thông tin suất chiếu và phòng chiếu.
     * @param requestedIds Danh sách ID các ghế cần giữ (tối đa 8 ghế).
     * @param currentToken Hold token hiện tại.
     * @return HoldResult chứa thông tin holdToken và danh sách bản ghi BookingTicket tạm giữ.
     */
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

        Showtime showtime = showtimeRepository.findById(selection.showtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));
        Map<String, SeatType> seatTypes = seatTypesByCode();
        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size() || seats.stream().anyMatch(seat ->
                !selection.roomId().equals(seat.getRoomId()) || !isSellableSeat(seat, seatTypes))) {
            throw new IllegalArgumentException("Danh sách ghế không hợp lệ hoặc có ghế không thể bán.");
        }
        if (!ticketRepository.findByShowtimeIdAndSeatIdIn(selection.showtimeId(), seatIds).isEmpty()) {
            throw new IllegalArgumentException("Một hoặc nhiều ghế vừa được người khác chọn. Vui lòng chọn lại.");
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(HOLD_MINUTES);
        List<BookingTicket> holds = new ArrayList<>();
        for (Seat seat : seats) {
            BookingTicket ticket = new BookingTicket();
            String type = normalizeType(seat.getSeatType());
            ticket.setShowtimeId(selection.showtimeId());
            ticket.setSeatId(seat.getId());
            ticket.setSeatLabel(seat.getSeatLabel());
            ticket.setSeatType(type);
            ticket.setPrice(priceFor(showtime, seat));
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

    /** Giải phóng giải nén tất cả ghế đang giữ theo token chỉ định. */
    @Transactional
    public void releaseHold(String token) {
        if (token != null && !token.isBlank()) {
            ticketRepository.deleteUnbookedByHoldToken(token);
        }
    }

    /** Xóa các trạng thái giữ ghế đã hết hạn 5 phút trong CSDL. */
    @Transactional
    public void releaseExpired() {
        ticketRepository.deleteByStatusAndHoldExpiresAtBefore(BookingTicket.Status.HOLDING, LocalDateTime.now());
    }

    /** Tính giá vé cơ bản của vị trí ghế trong suất chiếu. */
    public BigDecimal priceFor(Showtime showtime, Seat seat) {
        return BigDecimal.valueOf(ticketService.calculatePrice(showtime, seat, "ADULT"))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BookingSeatView toView(Seat seat,
                                   BookingTicket ticket,
                                   String ownToken,
                                   Map<String, SeatType> seatTypes,
                                   Showtime showtime) {
        String type = normalizeType(seat.getSeatType());
        SeatType meta = seatTypes.get(type);
        boolean sellable = isSellableSeat(seat, seatTypes);
        String status;
        if (!sellable) {
            status = "UNAVAILABLE";
        } else if (ticket == null) {
            status = "AVAILABLE";
        } else if (ticket.getStatus() == BookingTicket.Status.BOOKED) {
            status = "BOOKED";
        } else if (Objects.equals(ticket.getHoldToken(), ownToken)) {
            status = "SELECTED";
        } else {
            status = "HOLDING";
        }

        return new BookingSeatView(
                seat.getId(),
                seat.getRowIndex(),
                seat.getColIndex(),
                seat.getSeatLabel(),
                type,
                displayName(type, meta),
                color(meta),
                visualCapacity(type, meta),
                sellable,
                status,
                sellable ? priceFor(showtime, seat) : BigDecimal.ZERO
        );
    }

    private Map<String, SeatType> seatTypesByCode() {
        Map<String, SeatType> map = new LinkedHashMap<>();
        seatTypeRepository.findAllByOrderByIdAsc().forEach(type -> map.put(normalizeType(type.getCode()), type));
        return map;
    }

    private boolean isSellableSeat(Seat seat, Map<String, SeatType> seatTypes) {
        String type = normalizeType(seat.getSeatType());
        if ("skip".equals(type)) {
            return false;
        }
        SeatType meta = seatTypes.get(type);
        if (meta == null) {
            return false;
        }
        return meta.isActive() && meta.isSellable() && meta.getCapacity() > 0;
    }

    private int visualCapacity(String type, SeatType meta) {
        if ("skip".equals(type)) {
            return 0;
        }
        return meta != null && meta.getCapacity() > 1 ? meta.getCapacity() : 1;
    }

    private String displayName(String type, SeatType meta) {
        if (meta != null && meta.getDisplayName() != null && !meta.getDisplayName().isBlank()) {
            return meta.getDisplayName();
        }
        return switch (type) {
            case "vip" -> "Ghế VIP";
            case "couple" -> "Ghế đôi";
            case "broken" -> "Ghế hỏng";
            case "empty" -> "Lối đi / Trống";
            default -> "Ghế thường";
        };
    }

    private String color(SeatType meta) {
        if (meta == null || meta.getColor() == null || meta.getColor().isBlank()) {
            return "#e2e8f0";
        }
        return meta.getColor();
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "std" : type.trim().toLowerCase();
    }

    public record HoldResult(String token, LocalDateTime expiresAt, List<BookingTicket> tickets) { }
}

