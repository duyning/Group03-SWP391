package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Seat map loading and temporary seat holding for customer booking.
 * Updated on 2026-07-03: Customer booking now renders the same configured seat layout and seat types as management.
 * Created by: HuyPB - HE191335
 */

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
    // Mỗi hold sống 5 phút; frontend chỉ hiển thị, backend kiểm tra hạn thật.
    public static final int HOLD_MINUTES = 5;

    // Giới hạn theo sức chứa, vì một record ghế đôi có capacity=2.
    public static final int MAX_SEAT_CAPACITY_PER_BOOKING = 8;

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
        // Lưu các dependency được inject để đọc layout, trạng thái hold và tính giá.
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.showtimeRepository = showtimeRepository;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public List<SeatType> getActiveSeatTypes() {
        // Chỉ đọc loại ghế active và giữ thứ tự ID cấu hình.
        return seatTypeRepository.findByActiveTrueOrderByIdAsc().stream()
                // Tạo bản hiển thị đã sửa tên/color nhưng không thay đổi entity database gốc.
                .map(this::toDisplaySeatType)
                .toList();
    }

    @Transactional
    public List<BookingSeatView> getSeatMap(BookingSelection selection, String ownToken) {
        // Xóa các dòng HOLDING quá hạn để ghế xuất hiện AVAILABLE ngay trong request này.
        releaseExpired();

        // Xác minh phòng trong BookingSelection vẫn tồn tại.
        Room room = roomRepository.findById(selection.roomId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng chiếu."));

        // Đọc Showtime để tính giá từng ghế theo ngày/giờ/format.
        Showtime showtime = showtimeRepository.findById(selection.showtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));

        // Map code → SeatType phục vụ kiểm tra bán được, capacity, display name và màu.
        Map<String, SeatType> seatTypes = seatTypesByCode();

        // Map mỗi seatId về trạng thái BookingTicket của đúng suất.
        Map<Long, BookingTicket> states = ticketRepository.findByShowtimeId(selection.showtimeId()).stream()
                .collect(Collectors.toMap(BookingTicket::getSeatId, Function.identity(), (first, ignored) -> first));

        // Duyệt layout vật lý theo hàng/cột để trả đúng thứ tự render.
        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId()).stream()
                // Ô skip chỉ giữ khoảng trống layout nên không tạo BookingSeatView.
                .filter(seat -> !"skip".equals(normalizeType(seat.getSeatType())))
                // Ghép Seat + trạng thái ticket + ownToken + giá thành DTO giao diện.
                .map(seat -> toView(seat, states.get(seat.getId()), ownToken, seatTypes, showtime))
                .toList();
    }

    @Transactional
    public HoldResult holdSeats(BookingSelection selection, Collection<Long> requestedIds, String currentToken) {
        // Request không có seatIds không thể tạo hold.
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một ghế.");
        }

        // LinkedHashSet vừa loại ID trùng vừa giữ thứ tự form gửi.
        LinkedHashSet<Long> seatIds = new LinkedHashSet<>(requestedIds);

        // Chặn nhanh số record trước khi query; kiểm tra capacity chính xác nằm phía dưới.
        if (seatIds.size() > MAX_SEAT_CAPACITY_PER_BOOKING) {
            throw new IllegalArgumentException("Mỗi lần đặt tối đa 8 ghế.");
        }

        // Dọn hold hết hạn để chúng không bị xem là xung đột.
        releaseExpired();

        // Tạo UUID cho phiên chọn mới hoặc tái sử dụng token khi người dùng chọn lại.
        String token = currentToken == null || currentToken.isBlank()
                ? UUID.randomUUID().toString() : currentToken;

        // Showtime phải còn tồn tại để tính giá.
        Showtime showtime = showtimeRepository.findById(selection.showtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));

        // Nạp metadata loại ghế một lần cho toàn bộ validation.
        Map<String, SeatType> seatTypes = seatTypesByCode();

        // findAllById đọc các Seat thật thay vì tin ID phía browser.
        List<Seat> seats = seatRepository.findAllById(seatIds);

        // Phải tìm đủ mọi ID, đúng phòng selection và đều là ghế bán được.
        if (seats.size() != seatIds.size() || seats.stream().anyMatch(seat ->
                !selection.roomId().equals(seat.getRoomId()) || !isSellableSeat(seat, seatTypes))) {
            throw new IllegalArgumentException("Danh sách ghế không hợp lệ hoặc có ghế không thể bán.");
        }

        // Cộng capacity để ghế đôi tính là 2 chỗ.
        int selectedCapacity = seats.stream()
                .mapToInt(seat -> seatCapacity(seat, seatTypes))
                .sum();

        // Giới hạn thật dựa trên capacity.
        if (selectedCapacity > MAX_SEAT_CAPACITY_PER_BOOKING) {
            throw new IllegalArgumentException("Mỗi lần đặt tối đa 8 ghế; một ghế đôi được tính là 2 ghế.");
        }

        // Query các ticket cùng showtime và nằm trong tập seatId đang chọn.
        boolean hasConflictingHold = ticketRepository.findByShowtimeIdAndSeatIdIn(selection.showtimeId(), seatIds)
                .stream()
                // BOOKED, đã gắn booking hoặc thuộc token khác đều là xung đột.
                .anyMatch(ticket -> ticket.getStatus() == BookingTicket.Status.BOOKED
                        || ticket.getBookingId() != null
                        || !Objects.equals(ticket.getHoldToken(), token));

        // Dừng trước khi xóa hold cũ của chính token.
        if (hasConflictingHold) {
            throw new IllegalArgumentException("Một hoặc nhiều ghế vừa được người khác chọn. Vui lòng chọn lại.");
        }

        // Mọi ghế trong nhóm dùng chung một hạn để bộ đếm nhất quán.
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(HOLD_MINUTES);

        // Danh sách entity mới sẽ thay thế hold cũ của token.
        List<BookingTicket> holds = new ArrayList<>();

        // Tạo một BookingTicket HOLDING cho từng Seat đã xác minh.
        for (Seat seat : seats) {
            BookingTicket ticket = new BookingTicket();

            // Chuẩn hóa loại ghế để snapshot không phụ thuộc cách viết hoa.
            String type = normalizeType(seat.getSeatType());

            // Sao chép showtime, seat và thông tin hiển thị vào dòng hold.
            ticket.setShowtimeId(selection.showtimeId());
            ticket.setSeatId(seat.getId());
            ticket.setSeatLabel(seat.getSeatLabel());
            ticket.setSeatType(type);

            // Giá được backend tính bằng TicketService, không nhận data-price của HTML.
            ticket.setPrice(priceFor(showtime, seat));

            // HOLDING + token + expiresAt xác định quyền tạm thời của phiên.
            ticket.setStatus(BookingTicket.Status.HOLDING);
            ticket.setHoldToken(token);
            ticket.setHoldExpiresAt(expiresAt);

            // Thêm entity vào batch chuẩn bị lưu.
            holds.add(ticket);
        }

        try {
            // Sau khi toàn bộ input hợp lệ mới xóa hold cũ của cùng token.
            ticketRepository.deleteUnbookedByHoldToken(token);

            // Đẩy DELETE xuống DB trước INSERT để unique constraint không va với hold cũ.
            ticketRepository.flush();

            // Batch insert và flush ngay; unique(showtime_id, seat_id) chặn race condition cuối cùng.
            ticketRepository.saveAllAndFlush(holds);
        } catch (DataIntegrityViolationException ex) {
            // Hai transaction cùng chọn ghế: transaction thua unique constraint nhận thông báo thân thiện.
            throw new IllegalArgumentException("Ghế vừa được người khác giữ. Vui lòng tải lại sơ đồ ghế.");
        }

        // Sắp xếp label để summary hiển thị A1, A2... ổn định.
        holds.sort(Comparator.comparing(BookingTicket::getSeatLabel));

        // Record trả token/hạn/danh sách hold về controller để lưu session.
        return new HoldResult(token, expiresAt, holds);
    }

    @Transactional
    public void releaseHold(String token) {
        // Không gọi DELETE với token null/rỗng.
        if (token != null && !token.isBlank()) {
            // Chỉ xóa dòng chưa gắn booking; ghế BOOKED không bị ảnh hưởng.
            ticketRepository.deleteUnbookedByHoldToken(token);
        }
    }

    @Transactional
    public void releaseExpired() {
        // Xóa mọi HOLDING có expiresAt trước thời điểm hiện tại.
        ticketRepository.deleteByStatusAndHoldExpiresAtBefore(BookingTicket.Status.HOLDING, LocalDateTime.now());
    }

    public BigDecimal priceFor(Showtime showtime, Seat seat) {
        // TicketService áp dụng base price, loại ghế, format/ngày; làm tròn về đơn vị đồng.
        return BigDecimal.valueOf(ticketService.calculatePrice(showtime, seat, "ADULT"))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BookingSeatView toView(Seat seat,
                                   BookingTicket ticket,
                                   String ownToken,
                                   Map<String, SeatType> seatTypes,
                                   Showtime showtime) {
        // Chuẩn hóa code loại ghế lưu trong Seat.
        String type = normalizeType(seat.getSeatType());

        // Metadata có thể null nếu code ghế không còn trong bảng cấu hình.
        SeatType meta = seatTypes.get(type);

        // Kiểm tra active/sellable/capacity trước khi quyết định trạng thái UI.
        boolean sellable = isSellableSeat(seat, seatTypes);

        // status được template ánh xạ thành class CSS và quyền tạo checkbox.
        String status;

        // Ghế cấu hình không bán luôn là UNAVAILABLE.
        if (!sellable) {
            status = "UNAVAILABLE";
        } else if (ticket == null) {
            // Không có dòng BookingTicket nghĩa là ghế đang trống.
            status = "AVAILABLE";
        } else if (ticket.getStatus() == BookingTicket.Status.BOOKED) {
            // BOOKED là ghế đã chốt sau thanh toán.
            status = "BOOKED";
        } else if (Objects.equals(ticket.getHoldToken(), ownToken)) {
            // Hold của chính session hiện tại được hiển thị SELECTED.
            status = "SELECTED";
        } else {
            // Hold còn lại thuộc khách khác.
            status = "HOLDING";
        }

        // DTO phẳng chứa toàn bộ dữ liệu Thymeleaf cần để dựng một ghế.
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
                // Giá chỉ có ý nghĩa cho ghế bán được; lúc submit service sẽ tính lại.
                sellable ? priceFor(showtime, seat) : BigDecimal.ZERO
        );
    }

    private Map<String, SeatType> seatTypesByCode() {
        // LinkedHashMap giữ thứ tự cấu hình và cho phép dữ liệu trùng code dùng bản ghi sau.
        Map<String, SeatType> map = new LinkedHashMap<>();

        // Chuẩn hóa code trước khi làm key để lookup không phân biệt hoa thường/khoảng trắng.
        seatTypeRepository.findAllByOrderByIdAsc().forEach(type -> map.put(normalizeType(type.getCode()), type));

        // Trả map dùng chung cho một request.
        return map;
    }

    private boolean isSellableSeat(Seat seat, Map<String, SeatType> seatTypes) {
        // Lấy code chuẩn hóa của Seat.
        String type = normalizeType(seat.getSeatType());

        // skip chỉ là ô trống layout.
        if ("skip".equals(type)) {
            return false;
        }

        // Không có metadata nghĩa là không đủ cấu hình để bán an toàn.
        SeatType meta = seatTypes.get(type);
        if (meta == null) {
            return false;
        }

        // Cần đồng thời active, sellable và capacity dương.
        return meta.isActive() && meta.isSellable() && meta.getCapacity() > 0;
    }

    private int visualCapacity(String type, SeatType meta) {
        // Ô skip không chiếm ghế/chỗ ngồi.
        if ("skip".equals(type)) {
            return 0;
        }

        // Metadata thiếu dùng 0 trước khi áp dụng fallback bên dưới.
        int configuredCapacity = meta != null ? meta.getCapacity() : 0;

        // Ghế couple luôn ít nhất 2 chỗ kể cả dữ liệu cũ cấu hình sai.
        if ("couple".equals(type)) {
            return Math.max(2, configuredCapacity);
        }

        // Mọi ghế thường khác chiếm ít nhất 1 chỗ.
        return Math.max(1, configuredCapacity);
    }

    private int seatCapacity(Seat seat, Map<String, SeatType> seatTypes) {
        // Chuẩn hóa code rồi tái sử dụng cùng quy tắc capacity dùng khi render.
        String type = normalizeType(seat.getSeatType());
        return visualCapacity(type, seatTypes.get(type));
    }

    private String displayName(String type, SeatType meta) {
        // Các loại hệ thống có nhãn cố định để trang chọn ghế luôn hiển thị đúng
        // ngay cả trước khi migration dữ liệu cũ hoàn tất.
        String canonicalName = switch (type) {
            case "std" -> "Ghế thường";
            case "vip" -> "Ghế VIP";
            case "couple" -> "Ghế đôi";
            case "broken" -> "Ghế hỏng";
            case "empty" -> "Lối đi / Trống";
            default -> null;
        };
        if (canonicalName != null) {
            return canonicalName;
        }
        if (meta != null && meta.getDisplayName() != null && !meta.getDisplayName().isBlank()) {
            return meta.getDisplayName();
        }
        return "Ghế thường";
    }

    private SeatType toDisplaySeatType(SeatType type) {
        // Tạo bản sao dùng cho view để không sửa entity đang được Hibernate quản lý.
        return new SeatType(
                type.getId(),
                type.getCode(),
                displayName(normalizeType(type.getCode()), type),
                type.getColor(),
                visualCapacity(normalizeType(type.getCode()), type),
                type.isSellable(),
                type.isActive()
        );
    }

    private String color(SeatType meta) {
        // Màu fallback xám nhạt giúp CSS luôn nhận một giá trị hợp lệ.
        if (meta == null || meta.getColor() == null || meta.getColor().isBlank()) {
            return "#e2e8f0";
        }

        // Dùng màu cấu hình khi có.
        return meta.getColor();
    }

    private String normalizeType(String type) {
        // Dữ liệu cũ thiếu code mặc định standard; còn lại trim/lowercase.
        return type == null || type.isBlank() ? "std" : type.trim().toLowerCase();
    }

    public record HoldResult(String token, LocalDateTime expiresAt, List<BookingTicket> tickets) { }
}
