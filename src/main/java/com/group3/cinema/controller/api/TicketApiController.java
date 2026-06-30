/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: TicketApiController.java
 * Người sửa: TrienLX
 */
package com.group3.cinema.controller.api;

import com.group3.cinema.entity.*;
import com.group3.cinema.service.TicketService;
import com.group3.cinema.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketApiController {

    private final TicketService ticketService;
    private final TicketPriceConfigRepository ticketPriceConfigRepository;
    private final SeatTypeSurchargeRepository seatTypeSurchargeRepository;
    private final FormatSurchargeRepository formatSurchargeRepository;
    private final CustomerDiscountRepository customerDiscountRepository;

    public TicketApiController(TicketService ticketService,
                               TicketPriceConfigRepository ticketPriceConfigRepository,
                               SeatTypeSurchargeRepository seatTypeSurchargeRepository,
                               FormatSurchargeRepository formatSurchargeRepository,
                               CustomerDiscountRepository customerDiscountRepository) {
        this.ticketService = ticketService;
        this.ticketPriceConfigRepository = ticketPriceConfigRepository;
        this.seatTypeSurchargeRepository = seatTypeSurchargeRepository;
        this.formatSurchargeRepository = formatSurchargeRepository;
        this.customerDiscountRepository = customerDiscountRepository;
    }

    /**
     * Lấy danh sách vé đã bán hoạt động của một suất chiếu.
     */
    @GetMapping("/showtime/{showtimeId}")
    public ResponseEntity<List<Ticket>> getTicketsByShowtime(@PathVariable("showtimeId") Long showtimeId) {
        return ResponseEntity.ok(ticketService.getTicketsByShowtime(showtimeId));
    }

    /**
     * Lấy danh sách tất cả các ghế cấu hình trong phòng chiếu của suất chiếu.
     */
    @GetMapping("/seats")
    public ResponseEntity<?> getSeatsByShowtime(@RequestParam("showtimeId") Long showtimeId) {
        try {
            return ResponseEntity.ok(ticketService.getSeatsForShowtime(showtimeId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy thống kê tỉ lệ lấp đầy của suất chiếu.
     */
    @GetMapping("/stats/{showtimeId}")
    public ResponseEntity<Map<String, Object>> getShowtimeStats(@PathVariable("showtimeId") Long showtimeId) {
        return ResponseEntity.ok(ticketService.getShowtimeStats(showtimeId));
    }

    /**
     * Bán vé mới (Thêm vé).
     */
    @PostMapping("/sell")
    public ResponseEntity<?> sellTicket(@RequestParam("showtimeId") Long showtimeId,
                                        @RequestParam("seatId") Long seatId,
                                        @RequestParam("customerType") String customerType,
                                        @RequestParam(value = "customerName", required = false) String customerName,
                                        @RequestParam(value = "customerPhone", required = false) String customerPhone) {
        try {
            return ResponseEntity.ok(ticketService.sellTicket(showtimeId, seatId, customerType, customerName, customerPhone));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Sửa vé (Cập nhật loại khách hàng).
     */
    @PutMapping("/{id}/update-customer")
    public ResponseEntity<?> updateCustomerType(@PathVariable("id") Long id,
                                                @RequestParam("customerType") String customerType) {
        try {
            return ResponseEntity.ok(ticketService.updateCustomerType(id, customerType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Hủy vé (Xóa vé - Đặt cờ deleted = true, giữ lịch sử trong DB).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTicket(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(ticketService.cancelTicket(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gợi ý phân tích giá cho một ghế trống trước khi bán vé.
     */
    @GetMapping("/price-suggestion")
    public ResponseEntity<?> getPriceSuggestionForSeat(@RequestParam("showtimeId") Long showtimeId,
                                                       @RequestParam("seatId") Long seatId,
                                                       @RequestParam("customerType") String customerType) {
        try {
            return ResponseEntity.ok(ticketService.getPriceBreakdownForSeat(showtimeId, seatId, customerType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gợi ý phân tích giá cho một vé đã bán (để phục vụ sửa thông tin vé).
     */
    @GetMapping("/{id}/price-suggestion")
    public ResponseEntity<?> getPriceSuggestionForTicket(@PathVariable("id") Long id,
                                                         @RequestParam("customerType") String customerType) {
        try {
            return ResponseEntity.ok(ticketService.getPriceBreakdownForTicket(id, customerType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy cấu hình ma trận giá.
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        Map<String, Object> data = new HashMap<>();
        data.put("basePrices", ticketPriceConfigRepository.findAll());
        data.put("seatSurcharges", seatTypeSurchargeRepository.findAll());
        data.put("formatSurcharges", formatSurchargeRepository.findAll());
        data.put("customerDiscounts", customerDiscountRepository.findAll());
        return ResponseEntity.ok(data);
    }

    @PostMapping("/configs/base")
    public ResponseEntity<?> updateBasePrice(@RequestBody TicketPriceConfig req) {
        Optional<TicketPriceConfig> configOpt = ticketPriceConfigRepository.findById(req.getId());
        if (!configOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
        }
        TicketPriceConfig config = configOpt.get();
        config.setBasePrice(req.getBasePrice());
        return ResponseEntity.ok(ticketPriceConfigRepository.save(config));
    }

    @PostMapping("/configs/seats")
    public ResponseEntity<?> updateSeatSurcharge(@RequestBody SeatTypeSurcharge req) {
        Optional<SeatTypeSurcharge> surchargeOpt = seatTypeSurchargeRepository.findBySeatTypeCode(req.getSeatTypeCode());
        if (!surchargeOpt.isPresent()) {
            surchargeOpt = seatTypeSurchargeRepository.findById(req.getId());
        }
        if (!surchargeOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
        }
        SeatTypeSurcharge surcharge = surchargeOpt.get();
        surcharge.setSurchargeAmount(req.getSurchargeAmount());
        return ResponseEntity.ok(seatTypeSurchargeRepository.save(surcharge));
    }

    @PostMapping("/configs/formats")
    public ResponseEntity<?> updateFormatSurcharge(@RequestBody FormatSurcharge req) {
        Optional<FormatSurcharge> surchargeOpt = formatSurchargeRepository.findByFormatCode(req.getFormatCode());
        if (!surchargeOpt.isPresent()) {
            surchargeOpt = formatSurchargeRepository.findById(req.getId());
        }
        if (!surchargeOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
        }
        FormatSurcharge surcharge = surchargeOpt.get();
        surcharge.setSurchargeAmount(req.getSurchargeAmount());
        return ResponseEntity.ok(formatSurchargeRepository.save(surcharge));
    }

    @PostMapping("/configs/discounts")
    public ResponseEntity<?> updateCustomerDiscount(@RequestBody CustomerDiscount req) {
        Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findByCustomerType(req.getCustomerType());
        if (!discountOpt.isPresent()) {
            discountOpt = customerDiscountRepository.findById(req.getId());
        }
        if (!discountOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
        }
        CustomerDiscount discount = discountOpt.get();
        discount.setDiscountRate(req.getDiscountRate());
        discount.setFixedPriceWeekday(req.getFixedPriceWeekday());
        return ResponseEntity.ok(customerDiscountRepository.save(discount));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTickets(
            @RequestParam(value = "movieId", required = false) Integer movieId,
            @RequestParam(value = "room", required = false) String room,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(value = "searchTerm", required = false) String searchTerm) {
        try {
            return ResponseEntity.ok(ticketService.searchTickets(movieId, room, status, startDate, endDate, searchTerm));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/hold")
    public ResponseEntity<?> holdSeat(@RequestParam("showtimeId") Long showtimeId,
                                      @RequestParam("seatId") Long seatId) {
        try {
            return ResponseEntity.ok(ticketService.holdSeat(showtimeId, seatId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/release")
    public ResponseEntity<?> releaseSeat(@RequestParam("showtimeId") Long showtimeId,
                                         @RequestParam("seatId") Long seatId) {
        try {
            ticketService.releaseSeat(showtimeId, seatId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/change-seat")
    public ResponseEntity<?> changeSeat(@PathVariable("id") Long id,
                                        @RequestParam("newSeatId") Long newSeatId) {
        try {
            return ResponseEntity.ok(ticketService.changeSeat(id, newSeatId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
