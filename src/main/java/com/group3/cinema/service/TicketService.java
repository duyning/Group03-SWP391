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

import java.time.LocalTime;
import java.util.ArrayList;
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
        if (time.isBefore(LocalTime.of(12, 0))) {
            return "Suất sớm";
        }
        if (time.isBefore(LocalTime.of(17, 0))) {
            return "Giờ thường";
        }
        if (time.isBefore(LocalTime.of(22, 0))) {
            return "Giờ vàng";
        }
        return "Suất khuya";
    }

    public double calculatePrice(Showtime showtime, Seat seat, String customerType) {
        String dayType = showtime.getDayType();
        if (dayType == null || dayType.isBlank()) {
            dayType = "Trong tuần";
        }

        String slotName = determineTimeSlot(showtime.getShowTime());
        double basePrice = resolveBasePrice(dayType, slotName);
        double seatSurcharge = resolveSeatSurcharge(seat);
        double formatSurcharge = resolveFormatSurcharge(showtime);
        double finalPrice = basePrice + seatSurcharge + formatSurcharge;

        if (customerType != null && !"ADULT".equalsIgnoreCase(customerType)) {
            Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findByCustomerType(customerType);
            if (discountOpt.isPresent()) {
                CustomerDiscount discount = discountOpt.get();
                if ("Trong tuần".equals(dayType)
                        && discount.getFixedPriceWeekday() != null
                        && discount.getFixedPriceWeekday() > 0) {
                    finalPrice = discount.getFixedPriceWeekday() + seatSurcharge + formatSurcharge;
                } else {
                    finalPrice = finalPrice * (1 - discount.getDiscountRate());
                }
            }
        }

        return finalPrice;
    }

    private double resolveBasePrice(String dayType, String slotName) {
        Optional<TicketPriceConfig> configOpt = ticketPriceConfigRepository.findByDayTypeAndSlotName(dayType, slotName);
        if (configOpt.isPresent()) {
            return configOpt.get().getBasePrice();
        }

        if ("Trong tuần".equals(dayType)) {
            if ("Suất sớm".equals(slotName)) return 50000.0;
            if ("Giờ vàng".equals(slotName)) return 75000.0;
            if ("Suất khuya".equals(slotName)) return 65000.0;
            return 60000.0;
        }
        if ("Cuối tuần".equals(dayType)) {
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
        Optional<SeatTypeSurcharge> seatSurchargeOpt = seatTypeSurchargeRepository.findBySeatTypeCode(seatType);
        if (seatSurchargeOpt.isPresent()) {
            return seatSurchargeOpt.get().getSurchargeAmount();
        }
        if ("vip".equals(seatType)) {
            return 15000.0;
        }
        if ("couple".equals(seatType)) {
            return 30000.0;
        }
        return 0.0;
    }

    private double resolveFormatSurcharge(Showtime showtime) {
        if (showtime == null || showtime.getRoom() == null || showtime.getRoom().isBlank()) {
            return 0.0;
        }

        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isEmpty()) {
            return 0.0;
        }

        String formatCode = roomOpt.get().getRoomType();
        if (formatCode == null || formatCode.isBlank()) {
            return 0.0;
        }

        Optional<FormatSurcharge> formatOpt = formatSurchargeRepository.findByFormatCode(formatCode);
        if (formatOpt.isPresent()) {
            return formatOpt.get().getSurchargeAmount();
        }
        if ("3D".equalsIgnoreCase(formatCode)) {
            return 25000.0;
        }
        if ("IMAX".equalsIgnoreCase(formatCode)) {
            return 80000.0;
        }
        if ("Premium".equalsIgnoreCase(formatCode)) {
            return 50000.0;
        }
        return 0.0;
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
        if (showtime == null || showtime.getId() == null) {
            return;
        }

        boolean hasSold = ticketRepository.existsByShowtimeIdAndStatus(showtime.getId(), "Đã bán");
        if (hasSold) {
            return;
        }

        ticketRepository.deleteUnsoldTicketsByShowtimeId(showtime.getId());

        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isEmpty()) {
            return;
        }

        Room room = roomOpt.get();
        List<Seat> seats = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId());
        List<Ticket> ticketsToSave = new ArrayList<>();

        for (Seat seat : seats) {
            String seatType = normalizeSeatType(seat.getSeatType());
            if ("empty".equals(seatType) || "skip".equals(seatType) || "broken".equals(seatType)) {
                continue;
            }

            double calculatedPrice = calculatePrice(showtime, seat, "ADULT");

            Ticket ticket = new Ticket();
            ticket.setShowtime(showtime);
            ticket.setSeat(seat);
            ticket.setSeatNumber(seat.getSeatLabel());
            ticket.setSeatLabel(seat.getSeatLabel());
            ticket.setSeatType(displaySeatType(seat.getSeatType()));
            ticket.setBasePrice(calculatedPrice);
            ticket.setPrice(calculatedPrice);
            ticket.setStatus("Còn trống");
            ticket.setCustomerType("ADULT");

            ticketsToSave.add(ticket);
        }

        if (!ticketsToSave.isEmpty()) {
            ticketRepository.saveAll(ticketsToSave);
        }
    }

    @Transactional
    public List<Ticket> getTicketsByShowtime(Long showtimeId) {
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);
        if (tickets.isEmpty()) {
            Optional<Showtime> showtimeOpt = showtimeRepository.findById(showtimeId);
            if (showtimeOpt.isPresent()) {
                generateTicketsForShowtime(showtimeOpt.get());
                tickets = ticketRepository.findByShowtimeId(showtimeId);
            }
        }
        return tickets;
    }

    @Transactional
    public Ticket sellTicket(Long ticketId, String customerType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId));
        if ("Đã bán".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé này đã được bán trước đó!");
        }

        double finalPrice = calculatePrice(ticket.getShowtime(), ticket.getSeat(), customerType);
        ticket.setStatus("Đã bán");
        ticket.setCustomerType(customerType);
        ticket.setPrice(finalPrice);
        if (ticket.getBasePrice() <= 0) {
            ticket.setBasePrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
        }

        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateCustomerType(Long ticketId, String customerType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId));
        if (!"Đã bán".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé này chưa được bán, không thể cập nhật đối tượng!");
        }
        if (ticket.getBasePrice() <= 0) {
            ticket.setBasePrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
        }

        double newPrice = calculatePrice(ticket.getShowtime(), ticket.getSeat(), customerType);
        ticket.setCustomerType(customerType);
        ticket.setPrice(newPrice);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket toggleTicketStatus(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId));

        if ("Đã bán".equals(ticket.getStatus())) {
            ticket.setStatus("Còn trống");
            ticket.setCustomerType("ADULT");
            ticket.setPrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
        } else {
            ticket.setStatus("Đã bán");
            ticket.setCustomerType("ADULT");
            if (ticket.getBasePrice() <= 0) {
                ticket.setBasePrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
            }
        }

        return ticketRepository.save(ticket);
    }

    public Map<String, Object> getShowtimeStats(Long showtimeId) {
        long total = ticketRepository.countByShowtimeId(showtimeId);
        long sold = ticketRepository.countByShowtimeIdAndStatus(showtimeId, "Đã bán");
        long empty = total - sold;

        Double revenueVal = ticketRepository.calculateRevenueByShowtimeId(showtimeId);
        double revenue = revenueVal != null ? revenueVal : 0.0;
        double occupancyRate = total > 0 ? Math.round(((double) sold / total) * 1000.0) / 10.0 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", total);
        stats.put("soldCount", sold);
        stats.put("emptyCount", empty);
        stats.put("revenue", revenue);
        stats.put("occupancyRate", occupancyRate);
        return stats;
    }
}
