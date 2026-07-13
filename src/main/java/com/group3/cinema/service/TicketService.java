package com.group3.cinema.service;

/*
 * Service xử lý nghiệp vụ vé xem phim.
 * Created/updated by: NinhDD - HE186113, TrienLX
 */

import com.group3.cinema.entity.CustomerDiscount;
import com.group3.cinema.entity.FormatSurcharge;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.SeatTypeSurcharge;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.entity.TicketPriceConfig;
import com.group3.cinema.repository.CustomerDiscountRepository;
import com.group3.cinema.repository.FormatSurchargeRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.SeatTypeSurchargeRepository;
import com.group3.cinema.repository.TicketPriceConfigRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketPriceConfigRepository ticketPriceConfigRepository;
    private final SeatTypeSurchargeRepository seatTypeSurchargeRepository;
    private final FormatSurchargeRepository formatSurchargeRepository;
    private final CustomerDiscountRepository customerDiscountRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;

    public TicketService(TicketRepository ticketRepository,
                         TicketPriceConfigRepository ticketPriceConfigRepository,
                         SeatTypeSurchargeRepository seatTypeSurchargeRepository,
                         FormatSurchargeRepository formatSurchargeRepository,
                         CustomerDiscountRepository customerDiscountRepository,
                         RoomRepository roomRepository,
                         SeatRepository seatRepository,
                         ShowtimeRepository showtimeRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketPriceConfigRepository = ticketPriceConfigRepository;
        this.seatTypeSurchargeRepository = seatTypeSurchargeRepository;
        this.formatSurchargeRepository = formatSurchargeRepository;
        this.customerDiscountRepository = customerDiscountRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
    }

    public List<Ticket> getTicketsByAccount(int accountId) {
        return ticketRepository.findByAccountAccountIDOrderByBookingTimeDesc(accountId);
    }

    public Optional<Ticket> getTicketDetail(Long ticketId, int accountId) {
        return ticketRepository.findByIdAndAccountAccountID(ticketId, accountId);
    }

    public String determineTimeSlot(LocalTime time) {
        if (time == null) {
            return "Giờ thường";
        }
        int hour = time.getHour();
        if (hour < 12) {
            return "Suất sớm";
        }
        if (hour < 17) {
            return "Giờ thường";
        }
        if (hour < 22) {
            return "Giờ vàng";
        }
        return "Suất khuya";
    }

    public double calculatePrice(Showtime showtime, Seat seat, String customerType) {
        Ticket ticket = new Ticket();
        populateTicketPriceDetails(ticket, showtime, seat, customerType);
        return ticket.getFinalPrice();
    }

    public void populateTicketPriceDetails(Ticket ticket, Showtime showtime, Seat seat, String customerType) {
        if (ticket == null) {
            return;
        }

        String dayType = showtime != null && showtime.getDayType() != null && !showtime.getDayType().isBlank()
                ? showtime.getDayType()
                : "Trong tuần";
        String slotName = determineTimeSlot(showtime != null ? showtime.getShowTime() : null);
        String dateStr = showtime != null && showtime.getShowDate() != null ? showtime.getShowDate().toString() : "";
        Long movieId = showtime != null && showtime.getMovie() != null ? (long) showtime.getMovie().getId() : null;

        double basePrice = resolveBasePrice(movieId, dateStr, dayType, slotName);
        double seatSurcharge = resolveSeatSurcharge(seat);
        double formatSurcharge = resolveFormatSurcharge(showtime);
        double subtotal = basePrice + seatSurcharge + formatSurcharge;
        double discountAmount = resolveDiscountAmount(subtotal, dayType, seatSurcharge, formatSurcharge, customerType);
        double finalPrice = Math.max(0.0, subtotal - discountAmount);

        ticket.setBasePrice(basePrice);
        ticket.setSeatSurcharge(seatSurcharge);
        ticket.setFormatSurcharge(formatSurcharge);
        ticket.setDiscountAmount(discountAmount);
        ticket.setFinalPrice(finalPrice);
        ticket.setPrice(finalPrice);
    }

    private double resolveBasePrice(Long movieId, String dateStr, String dayType, String slotName) {
        List<TicketPriceConfig> configs = ticketPriceConfigRepository.findAll();

        Optional<TicketPriceConfig> matched = Optional.empty();
        if (movieId != null && dateStr != null && !dateStr.isBlank()) {
            matched = configs.stream()
                    .filter(c -> movieId.equals(c.getMovieId()))
                    .filter(c -> dateStr.equals(c.getDayType()))
                    .filter(c -> slotName.equals(c.getSlotName()))
                    .findFirst();
        }
        if (matched.isEmpty() && movieId != null) {
            matched = configs.stream()
                    .filter(c -> movieId.equals(c.getMovieId()))
                    .filter(c -> dayType.equalsIgnoreCase(c.getDayType()))
                    .filter(c -> slotName.equals(c.getSlotName()))
                    .findFirst();
        }
        if (matched.isEmpty() && dateStr != null && !dateStr.isBlank()) {
            matched = configs.stream()
                    .filter(c -> c.getMovieId() == null)
                    .filter(c -> dateStr.equals(c.getDayType()))
                    .filter(c -> slotName.equals(c.getSlotName()))
                    .findFirst();
        }
        if (matched.isEmpty()) {
            matched = configs.stream()
                    .filter(c -> c.getMovieId() == null)
                    .filter(c -> dayType.equalsIgnoreCase(c.getDayType()))
                    .filter(c -> slotName.equals(c.getSlotName()))
                    .findFirst();
        }

        return matched.map(TicketPriceConfig::getBasePrice)
                .orElseGet(() -> fallbackBasePrice(dayType, slotName));
    }

    private double fallbackBasePrice(String dayType, String slotName) {
        if ("Trong tuần".equalsIgnoreCase(dayType)) {
            if ("Suất sớm".equals(slotName)) return 50000.0;
            if ("Giờ vàng".equals(slotName)) return 75000.0;
            if ("Suất khuya".equals(slotName)) return 65000.0;
            return 60000.0;
        }
        if ("Cuối tuần".equalsIgnoreCase(dayType)) {
            if ("Suất sớm".equals(slotName)) return 65000.0;
            if ("Giờ vàng".equals(slotName)) return 95000.0;
            if ("Suất khuya".equals(slotName)) return 85000.0;
            return 80000.0;
        }
        if ("Suất sớm".equals(slotName)) return 80000.0;
        if ("Giờ vàng".equals(slotName)) return 110000.0;
        if ("Suất khuya".equals(slotName)) return 100000.0;
        return 95000.0;
    }

    private double resolveSeatSurcharge(Seat seat) {
        String seatType = normalizeSeatType(seat != null ? seat.getSeatType() : null);
        return seatTypeSurchargeRepository.findBySeatTypeCode(seatType)
                .map(SeatTypeSurcharge::getSurchargeAmount)
                .orElseGet(() -> {
                    if ("vip".equals(seatType)) return 15000.0;
                    if ("couple".equals(seatType)) return 30000.0;
                    return 0.0;
                });
    }

    private double resolveFormatSurcharge(Showtime showtime) {
        if (showtime == null || showtime.getRoom() == null || showtime.getRoom().isBlank()) {
            return 0.0;
        }

        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isEmpty() || roomOpt.get().getRoomType() == null || roomOpt.get().getRoomType().isBlank()) {
            return 0.0;
        }

        String formatCode = roomOpt.get().getRoomType();
        return formatSurchargeRepository.findByFormatCode(formatCode)
                .map(FormatSurcharge::getSurchargeAmount)
                .orElseGet(() -> {
                    if ("3D".equalsIgnoreCase(formatCode)) return 25000.0;
                    if ("IMAX".equalsIgnoreCase(formatCode)) return 80000.0;
                    if ("Premium".equalsIgnoreCase(formatCode)) return 50000.0;
                    return 0.0;
                });
    }

    private double resolveDiscountAmount(double subtotal,
                                         String dayType,
                                         double seatSurcharge,
                                         double formatSurcharge,
                                         String customerType) {
        if (customerType == null || "ADULT".equalsIgnoreCase(customerType)) {
            return 0.0;
        }

        Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findByCustomerType(customerType);
        if (discountOpt.isEmpty()) {
            return 0.0;
        }

        CustomerDiscount discount = discountOpt.get();
        if (subtotal < discount.getMinPriceToApply()) {
            return 0.0;
        }

        double discountAmount;
        if ("Trong tuần".equalsIgnoreCase(dayType)
                && discount.getFixedPriceWeekday() != null
                && discount.getFixedPriceWeekday() > 0) {
            double flatPrice = discount.getFixedPriceWeekday() + seatSurcharge + formatSurcharge;
            discountAmount = Math.max(0.0, subtotal - flatPrice);
        } else {
            discountAmount = subtotal * discount.getDiscountRate();
        }

        return Math.min(discountAmount, discount.getMaxDiscountAmount());
    }

    private String normalizeSeatType(String seatType) {
        if (seatType == null || seatType.isBlank()) {
            return "std";
        }
        String normalized = seatType.trim().toLowerCase();
        if ("standard".equals(normalized) || "thường".equals(normalized) || "thuong".equals(normalized)) {
            return "std";
        }
        if ("couple".equals(normalized) || "đôi".equals(normalized) || "doi".equals(normalized) || "sweetbox".equals(normalized)) {
            return "couple";
        }
        if ("vip".equals(normalized)) {
            return "vip";
        }
        return normalized;
    }

    private String displaySeatType(String seatType) {
        return switch (normalizeSeatType(seatType)) {
            case "vip" -> "VIP";
            case "couple" -> "Đôi";
            default -> "Thường";
        };
    }

    @Transactional
    public void generateTicketsForShowtime(Showtime showtime) {
        // Vé quản lý được tạo khi giữ/bán ghế, không sinh trước toàn bộ vé trống.
    }

    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByShowtime(Long showtimeId) {
        return ticketRepository.findByShowtimeIdAndDeletedFalse(showtimeId);
    }

    @Transactional(readOnly = true)
    public List<Seat> getSeatsForShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isEmpty()) {
            return Collections.emptyList();
        }
        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomOpt.get().getId());
    }

    @Transactional
    public Ticket holdSeat(Long showtimeId, Long seatId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        Optional<Ticket> existing = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId);
        if (existing.isPresent()) {
            throw new IllegalStateException("Ghế này đã được bán hoặc đang giữ chỗ!");
        }

        Ticket ticket = buildTicket(showtime, seat, "ADULT", "PENDING");
        return ticketRepository.save(ticket);
    }

    @Transactional
    public void releaseSeat(Long showtimeId, Long seatId) {
        ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId)
                .filter(ticket -> "PENDING".equals(ticket.getStatus()))
                .ifPresent(ticketRepository::delete);
    }

    @Transactional
    public Ticket sellTicket(Long showtimeId, Long seatId, String customerType) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        Optional<Ticket> existingOpt = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId);
        Ticket ticket = existingOpt.orElseGet(() -> buildTicket(showtime, seat, customerType, "BOOKED"));
        if ("BOOKED".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ghế này đã có vé đặt rồi!");
        }

        fillTicket(ticket, showtime, seat, customerType, "BOOKED");
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket sellTicket(Long ticketId, String customerType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId));
        if ("BOOKED".equals(ticket.getStatus()) || "Đã bán".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé này đã được bán trước đó!");
        }

        populateTicketPriceDetails(ticket, ticket.getShowtime(), ticket.getSeat(), customerType);
        ticket.setStatus("BOOKED");
        ticket.setCustomerType(customerType);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setDeleted(false);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket changeSeat(Long ticketId, Long newSeatId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));
        if (ticket.isDeleted() || "REFUNDED".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé đã bị hủy hoặc hoàn trả, không thể đổi ghế!");
        }

        Seat newSeat = seatRepository.findById(newSeatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế mới!"));
        ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(ticket.getShowtime().getId(), newSeatId)
                .filter(existing -> !existing.getId().equals(ticketId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("Ghế mới đã có vé đặt hoặc đang giữ chỗ!");
                });

        ticket.setSeat(newSeat);
        ticket.setSeatNumber(newSeat.getSeatLabel());
        ticket.setSeatType(displaySeatType(newSeat.getSeatType()));
        populateTicketPriceDetails(ticket, ticket.getShowtime(), newSeat, ticket.getCustomerType());
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateCustomerType(Long ticketId, String customerType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));
        if (ticket.isDeleted() || "REFUNDED".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé đã bị hủy hoặc hoàn trả, không thể chỉnh sửa!");
        }

        populateTicketPriceDetails(ticket, ticket.getShowtime(), ticket.getSeat(), customerType);
        ticket.setCustomerType(customerType);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket toggleTicketStatus(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId));
        if ("BOOKED".equals(ticket.getStatus()) || "Đã bán".equals(ticket.getStatus())) {
            ticket.setStatus("PENDING");
        } else {
            ticket.setStatus("BOOKED");
        }
        ticket.setDeleted(false);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));
        ticket.setDeleted(true);
        ticket.setStatus("REFUNDED");
        return ticketRepository.save(ticket);
    }

    public Map<String, Object> getShowtimeStats(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));

        long totalCount = 0;
        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isPresent()) {
            totalCount = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomOpt.get().getId()).stream()
                    .filter(seat -> {
                        String type = normalizeSeatType(seat.getSeatType());
                        return !"empty".equals(type) && !"skip".equals(type) && !"broken".equals(type);
                    })
                    .count();
        }

        long soldCount = ticketRepository.countByShowtimeIdAndStatusAndDeletedFalse(showtimeId, "BOOKED");
        long emptyCount = Math.max(0, totalCount - soldCount);
        Double revenueVal = ticketRepository.calculateRevenueByShowtimeId(showtimeId);
        double revenue = revenueVal != null ? revenueVal : 0.0;
        double occupancyRate = totalCount > 0 ? Math.round(((double) soldCount / totalCount) * 1000.0) / 10.0 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("soldCount", soldCount);
        stats.put("emptyCount", emptyCount);
        stats.put("revenue", revenue);
        stats.put("occupancyRate", occupancyRate);
        return stats;
    }

    public Map<String, Object> getPriceBreakdownForSeat(Long showtimeId, Long seatId, String customerType) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));
        return computeBreakdownMap(showtime, seat, customerType);
    }

    public Map<String, Object> getPriceBreakdownForTicket(Long ticketId, String customerType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));
        return computeBreakdownMap(ticket.getShowtime(), ticket.getSeat(), customerType);
    }

    private Map<String, Object> computeBreakdownMap(Showtime showtime, Seat seat, String customerType) {
        Ticket ticket = new Ticket();
        populateTicketPriceDetails(ticket, showtime, seat, customerType);
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("basePrice", ticket.getBasePrice());
        breakdown.put("seatSurcharge", ticket.getSeatSurcharge());
        breakdown.put("formatSurcharge", ticket.getFormatSurcharge());
        breakdown.put("discountAmount", ticket.getDiscountAmount());
        breakdown.put("finalPrice", ticket.getFinalPrice());
        return breakdown;
    }

    @Transactional
    public Ticket createTicket(Long showtimeId, Long seatId, String customerType, String status) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        if (ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId).isPresent()) {
            throw new IllegalStateException("Ghế này đã có vé đặt hoặc đang giữ chỗ!");
        }

        return ticketRepository.save(buildTicket(showtime, seat, customerType,
                status != null ? status : "BOOKED"));
    }

    @Transactional
    public Ticket updateTicket(Long ticketId, Long showtimeId, Long seatId, String customerType, String status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId)
                .filter(existing -> !existing.getId().equals(ticketId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("Ghế này đã có vé đặt bởi khách hàng khác!");
                });

        fillTicket(ticket, showtime, seat, customerType, status != null ? status : "BOOKED");
        return ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<Ticket> searchTickets(Integer movieId, String room, String status, java.time.LocalDate fromDate, java.time.LocalDate toDate, String searchTerm) {
        return ticketRepository.searchTickets(movieId, room, status, fromDate, toDate, searchTerm);
    }

    private Ticket buildTicket(Showtime showtime, Seat seat, String customerType, String status) {
        Ticket ticket = new Ticket();
        fillTicket(ticket, showtime, seat, customerType, status);
        return ticket;
    }

    private void fillTicket(Ticket ticket, Showtime showtime, Seat seat, String customerType, String status) {
        ticket.setShowtime(showtime);
        ticket.setSeat(seat);
        ticket.setSeatNumber(seat.getSeatLabel());
        ticket.setSeatType(displaySeatType(seat.getSeatType()));
        populateTicketPriceDetails(ticket, showtime, seat, customerType);
        ticket.setStatus(status);
        ticket.setCustomerType(customerType);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setDeleted(false);
    }
}
