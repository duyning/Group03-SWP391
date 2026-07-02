/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: TicketService.java
 * Người sửa: TrienLX
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
     * Xác định TimeSlot theo giờ chiếu.
     */
    public String determineTimeSlot(LocalTime time) {
        if (time == null) return "Giờ thường";
        int hour = time.getHour();
        if (hour < 12) {
            return "Suất sớm";
        } else if (hour < 17) {
            return "Giờ thường";
        } else if (hour < 22) {
            return "Giờ vàng";
        } else {
            return "Suất khuya";
        }
    }

    /**
     * Tính toán giá vé thực tế dựa trên Showtime, Seat và Đối tượng khách hàng.
     */
    public double calculatePrice(Showtime showtime, Seat seat, String customerType) {
        Ticket t = new Ticket();
        populateTicketPriceDetails(t, showtime, seat, customerType);
        return t.getFinalPrice();
    }

    /**
     * Tính toán chi tiết cơ cấu giá vé (basePrice, seatSurcharge, formatSurcharge, discountAmount, finalPrice).
     */
    public void populateTicketPriceDetails(Ticket ticket, Showtime showtime, Seat seat, String customerType) {
        // 1. Xác định Giá Cơ Bản theo Ngày chiếu và Khung giờ
        final String dayType = (showtime.getDayType() == null || showtime.getDayType().isEmpty())
            ? "Trong tuần" : showtime.getDayType();
        String slotName = determineTimeSlot(showtime.getShowTime());

        // Lấy tất cả cấu hình giá cơ bản từ DB để thực hiện so khớp phân cấp (Hierarchical matching)
        List<TicketPriceConfig> allConfigs = ticketPriceConfigRepository.findAll();
        String dateStr = showtime.getShowDate() != null ? showtime.getShowDate().toString() : ""; // YYYY-MM-DD
        Long movieId = showtime.getMovie() != null ? (long) showtime.getMovie().getId() : null;

        double basePriceVal = 60000.0;
        boolean matched = false;

        // Ưu tiên 1: Giá riêng cho Phim + Ngày cụ thể (Ví dụ ngày lễ đặc biệt cho phim hot)
        if (movieId != null && !dateStr.isEmpty()) {
            Optional<TicketPriceConfig> p1 = allConfigs.stream()
                .filter(c -> movieId.equals(c.getMovieId()) && dateStr.equals(c.getDayType()) && slotName.equals(c.getSlotName()))
                .findFirst();
            if (p1.isPresent()) {
                basePriceVal = p1.get().getBasePrice();
                matched = true;
            }
        }

        // Ưu tiên 2: Giá riêng cho Phim + Loại ngày (Trong tuần / Cuối tuần / Ngày lễ)
        if (!matched && movieId != null) {
            Optional<TicketPriceConfig> p2 = allConfigs.stream()
                .filter(c -> movieId.equals(c.getMovieId()) && dayType.equalsIgnoreCase(c.getDayType()) && slotName.equals(c.getSlotName()))
                .findFirst();
            if (p2.isPresent()) {
                basePriceVal = p2.get().getBasePrice();
                matched = true;
            }
        }

        // Ưu tiên 3: Giá chung (không theo phim) + Ngày cụ thể (Ví dụ ngày lễ đặc biệt áp dụng cho mọi phim)
        if (!matched && !dateStr.isEmpty()) {
            Optional<TicketPriceConfig> p3 = allConfigs.stream()
                .filter(c -> c.getMovieId() == null && dateStr.equals(c.getDayType()) && slotName.equals(c.getSlotName()))
                .findFirst();
            if (p3.isPresent()) {
                basePriceVal = p3.get().getBasePrice();
                matched = true;
            }
        }

        // Ưu tiên 4: Giá chung (không theo phim) + Loại ngày (Mặc định hệ thống)
        if (!matched) {
            Optional<TicketPriceConfig> p4 = allConfigs.stream()
                .filter(c -> c.getMovieId() == null && dayType.equalsIgnoreCase(c.getDayType()) && slotName.equals(c.getSlotName()))
                .findFirst();
            if (p4.isPresent()) {
                basePriceVal = p4.get().getBasePrice();
                matched = true;
            }
        }

        // Dự phòng mặc định nếu không khớp cấu hình nào
        if (!matched) {
            if ("Trong tuần".equalsIgnoreCase(dayType)) {
                if ("Suất sớm".equals(slotName)) basePriceVal = 50000.0;
                else if ("Giờ thường".equals(slotName)) basePriceVal = 60000.0;
                else if ("Giờ vàng".equals(slotName)) basePriceVal = 75000.0;
                else basePriceVal = 65000.0;
            } else if ("Cuối tuần".equalsIgnoreCase(dayType)) {
                if ("Suất sớm".equals(slotName)) basePriceVal = 65000.0;
                else if ("Giờ thường".equals(slotName)) basePriceVal = 80000.0;
                else if ("Giờ vàng".equals(slotName)) basePriceVal = 95000.0;
                else basePriceVal = 85000.0;
            } else {
                if ("Suất sớm".equals(slotName)) basePriceVal = 80000.0;
                else if ("Giờ thường".equals(slotName)) basePriceVal = 95000.0;
                else if ("Giờ vàng".equals(slotName)) basePriceVal = 110000.0;
                else basePriceVal = 100000.0;
            }
        }

        // 2. Xác định Phụ phí theo loại ghế
        String seatType = seat.getSeatType() != null ? seat.getSeatType().toLowerCase() : "std";
        if (seatType.equals("standard") || seatType.equals("thường")) {
            seatType = "std";
        } else if (seatType.equals("vip")) {
            seatType = "vip";
        } else if (seatType.equals("couple") || seatType.equals("đôi") || seatType.equals("sweetbox")) {
            seatType = "couple";
        }

        Optional<SeatTypeSurcharge> seatSurchargeOpt = seatTypeSurchargeRepository.findBySeatTypeCode(seatType);
        double seatSurchargeVal = 0.0;
        if (seatSurchargeOpt.isPresent()) {
            seatSurchargeVal = seatSurchargeOpt.get().getSurchargeAmount();
        } else {
            if ("vip".equals(seatType)) seatSurchargeVal = 15000.0;
            else if ("couple".equals(seatType)) seatSurchargeVal = 30000.0;
        }

        // 3. Xác định Phụ phí theo Định dạng phòng chiếu
        double formatSurchargeVal = 0.0;
        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        if (roomOpt.isPresent()) {
            String formatCode = roomOpt.get().getRoomType();
            if (formatCode != null && !formatCode.isEmpty()) {
                Optional<FormatSurcharge> formatOpt = formatSurchargeRepository.findByFormatCode(formatCode);
                if (formatOpt.isPresent()) {
                    formatSurchargeVal = formatOpt.get().getSurchargeAmount();
                } else if ("3D".equalsIgnoreCase(formatCode)) {
                    formatSurchargeVal = 25000.0;
                } else if ("IMAX".equalsIgnoreCase(formatCode) || "Premium".equalsIgnoreCase(formatCode)) {
                    formatSurchargeVal = 80000.0;
                }
            }
        }

        double subtotal = basePriceVal + seatSurchargeVal + formatSurchargeVal;
        double finalPriceVal = subtotal;
        double discountAmountVal = 0.0;

        // 4. Áp dụng Giảm giá theo Đối tượng khách hàng kèm kiểm tra ngưỡng áp dụng & giới hạn giảm tối đa
        if (customerType != null && !customerType.equals("ADULT")) {
            Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findByCustomerType(customerType);
            if (discountOpt.isPresent()) {
                CustomerDiscount discount = discountOpt.get();
                
                // Chỉ áp dụng giảm giá nếu tổng tiền vé chưa giảm >= ngưỡng tối thiểu để áp dụng
                if (subtotal >= discount.getMinPriceToApply()) {
                    if ("Trong tuần".equalsIgnoreCase(dayType) && discount.getFixedPriceWeekday() != null && discount.getFixedPriceWeekday() > 0) {
                        double flatPrice = discount.getFixedPriceWeekday() + seatSurchargeVal + formatSurchargeVal;
                        double potentialDiscount = subtotal - flatPrice;
                        if (potentialDiscount > 0) {
                            discountAmountVal = potentialDiscount;
                        }
                    } else {
                        discountAmountVal = subtotal * discount.getDiscountRate();
                    }

                    // Giới hạn số tiền giảm tối đa của vé
                    if (discountAmountVal > discount.getMaxDiscountAmount()) {
                        discountAmountVal = discount.getMaxDiscountAmount();
                    }

                    finalPriceVal = subtotal - discountAmountVal;
                }
            }
        }

        ticket.setBasePrice(basePriceVal);
        ticket.setSeatSurcharge(seatSurchargeVal);
        ticket.setFormatSurcharge(formatSurchargeVal);
        ticket.setDiscountAmount(discountAmountVal);
        ticket.setFinalPrice(finalPriceVal);
        ticket.setPrice(finalPriceVal); // Tương thích ngược
    }

    @Transactional
    public void generateTicketsForShowtime(Showtime showtime) {
        // Bỏ trống - vé chỉ tạo khi thực hiện đặt/bán
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
        if (!roomOpt.isPresent()) {
            return Collections.emptyList();
        }
        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomOpt.get().getId());
    }

    /**
     * Giữ chỗ tạm thời (chuyển trạng thái ghế sang PENDING).
     */
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

        Ticket ticket = new Ticket();
        ticket.setShowtime(showtime);
        ticket.setSeat(seat);
        ticket.setSeatNumber(seat.getSeatLabel());

        String type = seat.getSeatType();
        String vnType = "Thường";
        if ("vip".equalsIgnoreCase(type)) vnType = "VIP";
        else if ("couple".equalsIgnoreCase(type)) vnType = "Đôi";
        ticket.setSeatType(vnType);

        populateTicketPriceDetails(ticket, showtime, seat, "ADULT");
        ticket.setStatus("PENDING");
        ticket.setCreatedAt(java.time.LocalDateTime.now());
        ticket.setDeleted(false);

        return ticketRepository.save(ticket);
    }

    /**
     * Hủy giữ chỗ tạm thời (giải phóng ghế PENDING).
     */
    @Transactional
    public void releaseSeat(Long showtimeId, Long seatId) {
        Optional<Ticket> existing = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId);
        if (existing.isPresent()) {
            Ticket ticket = existing.get();
            if ("PENDING".equals(ticket.getStatus())) {
                ticketRepository.delete(ticket); // Xóa bản ghi giữ chỗ
            }
        }
    }

    /**
     * Bán vé mới (Thêm vé mới) - Tương thích ngược.
     */
    @Transactional
    public Ticket sellTicket(Long showtimeId, Long seatId, String customerType) {
        return sellTicket(showtimeId, seatId, customerType, null, null);
    }

    /**
     * Bán vé mới với thông tin đầy đủ đối tượng khách hàng, tên và số điện thoại.
     */
    @Transactional
    public Ticket sellTicket(Long showtimeId, Long seatId, String customerType, String customerName, String customerPhone) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        Optional<Ticket> existingOpt = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId);
        Ticket ticket;
        if (existingOpt.isPresent()) {
            ticket = existingOpt.get();
            if ("BOOKED".equals(ticket.getStatus())) {
                throw new IllegalStateException("Ghế này đã có vé đặt rồi!");
            }
            // Nếu trạng thái là PENDING, nâng cấp lên BOOKED
        } else {
            ticket = new Ticket();
            ticket.setShowtime(showtime);
            ticket.setSeat(seat);
            ticket.setSeatNumber(seat.getSeatLabel());

            String type = seat.getSeatType();
            String vnType = "Thường";
            if ("vip".equalsIgnoreCase(type)) vnType = "VIP";
            else if ("couple".equalsIgnoreCase(type)) vnType = "Đôi";
            ticket.setSeatType(vnType);
        }

        populateTicketPriceDetails(ticket, showtime, seat, customerType);
        ticket.setStatus("BOOKED");
        ticket.setCustomerType(customerType);
        ticket.setCustomerName(customerName);
        ticket.setCustomerPhone(customerPhone);
        ticket.setCreatedAt(java.time.LocalDateTime.now());
        ticket.setDeleted(false);

        return ticketRepository.save(ticket);
    }

    /**
     * Đổi ghế cùng suất chiếu cho vé đã đặt.
     */
    @Transactional
    public Ticket changeSeat(Long ticketId, Long newSeatId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));

        if (ticket.isDeleted() || "REFUNDED".equals(ticket.getStatus())) {
            throw new IllegalStateException("Vé đã bị hủy hoặc hoàn trả, không thể đổi ghế!");
        }

        Seat newSeat = seatRepository.findById(newSeatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế mới!"));

        Optional<Ticket> existing = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(ticket.getShowtime().getId(), newSeatId);
        if (existing.isPresent()) {
            throw new IllegalStateException("Ghế mới đã có vé đặt hoặc đang giữ chỗ!");
        }

        ticket.setSeat(newSeat);
        ticket.setSeatNumber(newSeat.getSeatLabel());
        String type = newSeat.getSeatType();
        String vnType = "Thường";
        if ("vip".equalsIgnoreCase(type)) vnType = "VIP";
        else if ("couple".equalsIgnoreCase(type)) vnType = "Đôi";
        ticket.setSeatType(vnType);

        populateTicketPriceDetails(ticket, ticket.getShowtime(), newSeat, ticket.getCustomerType());

        return ticketRepository.save(ticket);
    }

    /**
     * Sửa thông tin vé đã bán (Đổi đối tượng khách hàng).
     */
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

    /**
     * Hủy vé / Hoàn vé (Đổi status thành REFUNDED, deleted = true).
     */
    @Transactional
    public Ticket cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));

        ticket.setDeleted(true);
        ticket.setStatus("REFUNDED");

        return ticketRepository.save(ticket);
    }

    /**
     * Lấy thống kê tỉ lệ lấp đầy & doanh thu.
     */
    public Map<String, Object> getShowtimeStats(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));

        Optional<Room> roomOpt = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), 1L);
        long totalCount = 0;
        if (roomOpt.isPresent()) {
            List<Seat> seats = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomOpt.get().getId());
            totalCount = seats.stream().filter(s -> {
                String type = s.getSeatType();
                return type != null && !"empty".equalsIgnoreCase(type) && !"skip".equalsIgnoreCase(type) && !"broken".equalsIgnoreCase(type);
            }).count();
        }

        long soldCount = ticketRepository.countByShowtimeIdAndStatusAndDeletedFalse(showtimeId, "BOOKED");
        long emptyCount = totalCount - soldCount;

        Double revenueVal = ticketRepository.calculateRevenueByShowtimeId(showtimeId);
        double revenue = revenueVal != null ? revenueVal : 0.0;

        double occupancyRate = totalCount > 0 ? Math.round(((double) soldCount / totalCount) * 100 * 10) / 10.0 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("soldCount", soldCount);
        stats.put("emptyCount", emptyCount);
        stats.put("revenue", revenue);
        stats.put("occupancyRate", occupancyRate);

        return stats;
    }

    /**
     * Lấy chi tiết phân tích giá vé cho ghế cụ thể.
     */
    public Map<String, Object> getPriceBreakdownForSeat(Long showtimeId, Long seatId, String customerType) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        return computeBreakdownMap(showtime, seat, customerType);
    }

    /**
     * Lấy chi tiết phân tích giá vé cho vé chỉ định.
     */
    public Map<String, Object> getPriceBreakdownForTicket(Long ticketId, String customerType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));

        return computeBreakdownMap(ticket.getShowtime(), ticket.getSeat(), customerType);
    }

    private Map<String, Object> computeBreakdownMap(Showtime showtime, Seat seat, String customerType) {
        Ticket t = new Ticket();
        populateTicketPriceDetails(t, showtime, seat, customerType);
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("basePrice", t.getBasePrice());
        breakdown.put("seatSurcharge", t.getSeatSurcharge());
        breakdown.put("formatSurcharge", t.getFormatSurcharge());
        breakdown.put("discountAmount", t.getDiscountAmount());
        breakdown.put("finalPrice", t.getFinalPrice());
        return breakdown;
    }

    @Transactional
    public Ticket createTicket(Long showtimeId, Long seatId, String customerType, String customerName, String customerPhone, String status) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        Optional<Ticket> existingOpt = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId);
        if (existingOpt.isPresent()) {
            throw new IllegalStateException("Ghế này đã có vé đặt hoặc đang giữ chỗ!");
        }

        Ticket ticket = new Ticket();
        ticket.setShowtime(showtime);
        ticket.setSeat(seat);
        ticket.setSeatNumber(seat.getSeatLabel());

        String type = seat.getSeatType();
        String vnType = "Thường";
        if ("vip".equalsIgnoreCase(type)) vnType = "VIP";
        else if ("couple".equalsIgnoreCase(type)) vnType = "Đôi";
        ticket.setSeatType(vnType);

        populateTicketPriceDetails(ticket, showtime, seat, customerType);
        ticket.setStatus(status != null ? status : "BOOKED");
        ticket.setCustomerType(customerType);
        ticket.setCustomerName(customerName);
        ticket.setCustomerPhone(customerPhone);
        ticket.setCreatedAt(java.time.LocalDateTime.now());
        ticket.setDeleted(false);

        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateTicket(Long ticketId, Long showtimeId, Long seatId, String customerType, String customerName, String customerPhone, String status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vé!"));

        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu!"));
        
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ghế!"));

        Optional<Ticket> existingOpt = ticketRepository.findByShowtimeIdAndSeatIdAndDeletedFalse(showtimeId, seatId);
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(ticketId)) {
            throw new IllegalStateException("Ghế này đã có vé đặt bởi khách hàng khác!");
        }

        ticket.setShowtime(showtime);
        ticket.setSeat(seat);
        ticket.setSeatNumber(seat.getSeatLabel());

        String type = seat.getSeatType();
        String vnType = "Thường";
        if ("vip".equalsIgnoreCase(type)) vnType = "VIP";
        else if ("couple".equalsIgnoreCase(type)) vnType = "Đôi";
        ticket.setSeatType(vnType);

        populateTicketPriceDetails(ticket, showtime, seat, customerType);
        ticket.setStatus(status != null ? status : "BOOKED");
        ticket.setCustomerType(customerType);
        ticket.setCustomerName(customerName);
        ticket.setCustomerPhone(customerPhone);

        return ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<Ticket> searchTickets(Integer movieId, String room, String status, java.time.LocalDate fromDate, java.time.LocalDate toDate, String searchTerm) {
        return ticketRepository.searchTickets(movieId, room, status, fromDate, toDate, searchTerm);
    }
}
