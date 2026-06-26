/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: TicketService.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Xử lý logic nghiệp vụ cho Vé phim (sinh vé, tính toán giá vé theo công thức ma trận, thống kê).
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.*;
import com.group3.cinema.repository.*;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;

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

    /**
     * Xác định Khung giờ chiếu (Slot Name) dựa trên giờ chiếu thực tế.
     */
    public String determineTimeSlot(LocalTime time) {
        if (time == null) return "Giờ thường";
        if (time.isBefore(LocalTime.of(12, 0))) {
            return "Suất sớm"; // Trước 12:00
        } else if (time.isBefore(LocalTime.of(17, 0))) {
            return "Giờ thường"; // 12:00 - 16:59
        } else if (time.isBefore(LocalTime.of(22, 0))) {
            return "Giờ vàng"; // 17:00 - 21:59
        } else {
            return "Suất khuya"; // Sau 22:00
        }
    }

    /**
     * Tính toán giá vé thực tế dựa trên Showtime, Seat và Đối tượng khách hàng.
     */
    public double calculatePrice(Showtime showtime, Seat seat, String customerType) {
        // 1. Xác định Giá Cơ Bản theo Ngày chiếu và Khung giờ
        String dayType = showtime.getDayType();
        if (dayType == null || dayType.isEmpty()) {
            dayType = "Trong tuần";
        }
        String slotName = determineTimeSlot(showtime.getShowTime());

        Optional<TicketPriceConfig> configOpt = ticketPriceConfigRepository.findByDayTypeAndSlotName(dayType, slotName);
        double basePrice = 60000.0; // Giá trị fallback mặc định
        if (configOpt.isPresent()) {
            basePrice = configOpt.get().getBasePrice();
        } else {
            // Giá trị mặc định nếu chưa seed DB
            if ("Trong tuần".equals(dayType)) {
                if ("Suất sớm".equals(slotName)) basePrice = 50000.0;
                else if ("Giờ thường".equals(slotName)) basePrice = 60000.0;
                else if ("Giờ vàng".equals(slotName)) basePrice = 75000.0;
                else basePrice = 65000.0;
            } else if ("Cuối tuần".equals(dayType)) {
                if ("Suất sớm".equals(slotName)) basePrice = 65000.0;
                else if ("Giờ thường".equals(slotName)) basePrice = 80000.0;
                else if ("Giờ vàng".equals(slotName)) basePrice = 95000.0;
                else basePrice = 85000.0;
            } else { // Ngày lễ
                if ("Suất sớm".equals(slotName)) basePrice = 80000.0;
                else if ("Giờ thường".equals(slotName)) basePrice = 95000.0;
                else if ("Giờ vàng".equals(slotName)) basePrice = 110000.0;
                else basePrice = 100000.0;
            }
        }

        // 2. Xác định Phụ phí theo loại ghế
        String seatType = seat.getSeatType() != null ? seat.getSeatType().toLowerCase() : "std";
        // Đồng bộ chuẩn hóa code ghế
        if (seatType.equals("standard") || seatType.equals("thường")) {
            seatType = "std";
        } else if (seatType.equals("vip")) {
            seatType = "vip";
        } else if (seatType.equals("couple") || seatType.equals("đôi") || seatType.equals("sweetbox")) {
            seatType = "couple";
        }

        Optional<SeatTypeSurcharge> seatSurchargeOpt = seatTypeSurchargeRepository.findBySeatTypeCode(seatType);
        double seatSurcharge = 0.0;
        if (seatSurchargeOpt.isPresent()) {
            seatSurcharge = seatSurchargeOpt.get().getSurchargeAmount();
        } else {
            // Fallback mặc định
            if ("vip".equals(seatType)) seatSurcharge = 15000.0;
            else if ("couple".equals(seatType)) seatSurcharge = 30000.0;
        }

        // 3. Xác định Phụ phí theo Định dạng phòng chiếu
        double formatSurcharge = 0.0;
        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isPresent()) {
            String formatCode = roomOpt.get().getRoomType();
            if (formatCode != null && !formatCode.isEmpty()) {
                Optional<FormatSurcharge> formatOpt = formatSurchargeRepository.findByFormatCode(formatCode);
                if (formatOpt.isPresent()) {
                    formatSurcharge = formatOpt.get().getSurchargeAmount();
                } else if ("3D".equalsIgnoreCase(formatCode)) {
                    formatSurcharge = 25000.0;
                } else if ("IMAX".equalsIgnoreCase(formatCode) || "Premium".equalsIgnoreCase(formatCode)) {
                    formatSurcharge = 80000.0;
                }
            }
        }

        // Giá gốc của vé (Người lớn)
        double finalPrice = basePrice + seatSurcharge + formatSurcharge;

        // 4. Áp dụng Giảm giá theo Đối tượng khách hàng
        if (customerType != null && !customerType.equals("ADULT")) {
            Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findByCustomerType(customerType);
            if (discountOpt.isPresent()) {
                CustomerDiscount discount = discountOpt.get();
                // Nếu là ngày thường và có thiết lập đồng giá
                if ("Trong tuần".equals(dayType) && discount.getFixedPriceWeekday() != null && discount.getFixedPriceWeekday() > 0) {
                    finalPrice = discount.getFixedPriceWeekday() + seatSurcharge + formatSurcharge;
                } else {
                    finalPrice = finalPrice * (1 - discount.getDiscountRate());
                }
            }
        }

        return finalPrice;
    }

    /**
     * Sinh vé tự động cho suất chiếu.
     */
    @Transactional
    public void generateTicketsForShowtime(Showtime showtime) {
        if (showtime == null || showtime.getId() == null) return;

        // Nếu suất chiếu đã bán vé thì không tái tạo lại (tránh hỏng dữ liệu đặt chỗ)
        boolean hasSold = ticketRepository.existsByShowtimeIdAndStatus(showtime.getId(), "Đã bán");
        if (hasSold) {
            return;
        }

        // Xóa vé trống cũ của suất chiếu này để vẽ sơ đồ mới sạch sẽ
        ticketRepository.deleteUnsoldTicketsByShowtimeId(showtime.getId());

        // Lấy phòng chiếu để lấy sơ đồ ghế
        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (!roomOpt.isPresent()) {
            return;
        }

        Room room = roomOpt.get();
        List<Seat> seats = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId());

        List<Ticket> ticketsToSave = new ArrayList<>();
        for (Seat seat : seats) {
            // Bỏ qua các vị trí lối đi (empty) hoặc ô trống bị chiếm bởi ghế đôi (skip)
            String stType = seat.getSeatType();
            if ("empty".equalsIgnoreCase(stType) || "skip".equalsIgnoreCase(stType) || "broken".equalsIgnoreCase(stType)) {
                continue;
            }

            // Định dạng loại ghế để tương thích với frontend HTML/JS
            String ticketSeatType = "Thường";
            if ("vip".equalsIgnoreCase(stType)) {
                ticketSeatType = "VIP";
            } else if ("couple".equalsIgnoreCase(stType)) {
                ticketSeatType = "Đôi";
            }

            // Tính giá vé chuẩn (Người lớn)
            double calculatedPrice = calculatePrice(showtime, seat, "ADULT");

            Ticket ticket = new Ticket();
            ticket.setShowtime(showtime);
            ticket.setSeat(seat);
            ticket.setSeatNumber(seat.getSeatLabel());
            ticket.setSeatType(ticketSeatType);
            ticket.setPrice(calculatedPrice);
            ticket.setBasePrice(calculatedPrice);
            ticket.setStatus("Còn trống");
            ticket.setCustomerType("ADULT");

            ticketsToSave.add(ticket);
        }

        if (!ticketsToSave.isEmpty()) {
            ticketRepository.saveAll(ticketsToSave);
        }
    }

    /**
     * Lấy danh sách vé của 1 suất chiếu.
     */
    @Transactional
    public List<Ticket> getTicketsByShowtime(Long showtimeId) {
        List<Ticket> tickets = ticketRepository.findByShowtimeId(showtimeId);
        if (tickets.isEmpty()) {
            // Tự động sinh vé cho suất chiếu cũ nếu chưa có vé
            Optional<Showtime> showtimeOpt = showtimeRepository.findById(showtimeId);
            if (showtimeOpt.isPresent()) {
                generateTicketsForShowtime(showtimeOpt.get());
                tickets = ticketRepository.findByShowtimeId(showtimeId);
            }
        }
        return tickets;
    }

    /**
     * Bán vé cho khách hàng cụ thể.
     */
    @Transactional
    public Ticket sellTicket(Long ticketId, String customerType) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (!ticketOpt.isPresent()) {
            throw new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId);
        }

        Ticket ticket = ticketOpt.get();
        if ("Đã bán".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé này đã được bán trước đó!");
        }

        // Tính lại giá vé theo đối tượng khách hàng khi bán
        double finalPrice = calculatePrice(ticket.getShowtime(), ticket.getSeat(), customerType);

        ticket.setStatus("Đã bán");
        ticket.setCustomerType(customerType);
        ticket.setPrice(finalPrice);
        if (ticket.getBasePrice() <= 0) {
            ticket.setBasePrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
        }

        return ticketRepository.save(ticket);
    }

    /**
     * Cập nhật đối tượng khách hàng cho vé đã bán, đồng thời tính lại giá vé.
     */
    @Transactional
    public Ticket updateCustomerType(Long ticketId, String customerType) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (!ticketOpt.isPresent()) {
            throw new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId);
        }
        Ticket ticket = ticketOpt.get();
        if (!"Đã bán".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé này chưa được bán, không thể cập nhật đối tượng!");
        }
        if (ticket.getBasePrice() <= 0) {
            ticket.setBasePrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
        }
        // Tính lại giá vé theo đối tượng mới
        double newPrice = calculatePrice(ticket.getShowtime(), ticket.getSeat(), customerType);
        ticket.setCustomerType(customerType);
        ticket.setPrice(newPrice);
        return ticketRepository.save(ticket);
    }

    /**
     * Toggle trạng thái vé (dành cho Admin click bán nhanh trên sơ đồ).
     */
    @Transactional
    public Ticket toggleTicketStatus(Long ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (!ticketOpt.isPresent()) {
            throw new IllegalArgumentException("Không tìm thấy vé có ID: " + ticketId);
        }

        Ticket ticket = ticketOpt.get();
        if ("Đã bán".equals(ticket.getStatus())) {
            ticket.setStatus("Còn trống");
            ticket.setCustomerType("ADULT");
            // Khôi phục giá mặc định
            ticket.setPrice(calculatePrice(ticket.getShowtime(), ticket.getSeat(), "ADULT"));
        } else {
            ticket.setStatus("Đã bán");
            ticket.setCustomerType("ADULT");
        }

        return ticketRepository.save(ticket);
    }

    /**
     * Thống kê vé của suất chiếu.
     */
    public Map<String, Object> getShowtimeStats(Long showtimeId) {
        long total = ticketRepository.countByShowtimeId(showtimeId);
        long sold = ticketRepository.countByShowtimeIdAndStatus(showtimeId, "Đã bán");
        long empty = total - sold;

        Double revenueVal = ticketRepository.calculateRevenueByShowtimeId(showtimeId);
        double revenue = revenueVal != null ? revenueVal : 0.0;

        double occupancyRate = total > 0 ? Math.round(((double) sold / total) * 100 * 10) / 10.0 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", total);
        stats.put("soldCount", sold);
        stats.put("emptyCount", empty);
        stats.put("revenue", revenue);
        stats.put("occupancyRate", occupancyRate);

        return stats;
    }
}
