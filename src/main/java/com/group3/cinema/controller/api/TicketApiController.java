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

    @GetMapping("/price-calculation")
    public ResponseEntity<?> getPriceCalculation(@RequestParam("showtimeId") Long showtimeId,
                                                 @RequestParam("seatId") Long seatId,
                                                 @RequestParam("customerType") String customerType) {
        try {
            return ResponseEntity.ok(ticketService.getPriceBreakdownForSeat(showtimeId, seatId, customerType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/configs/base")
    public ResponseEntity<?> updateBasePrice(@RequestBody TicketPriceConfig req) {
        if (req.getId() != null) {
            Optional<TicketPriceConfig> configOpt = ticketPriceConfigRepository.findById(req.getId());
            if (!configOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
            }
            TicketPriceConfig config = configOpt.get();
            config.setDayType(req.getDayType());
            config.setSlotName(req.getSlotName());
            config.setStartTime(req.getStartTime());
            config.setEndTime(req.getEndTime());
            config.setBasePrice(req.getBasePrice());
            config.setMovieId(req.getMovieId());
            config.setNote(req.getNote());
            return ResponseEntity.ok(ticketPriceConfigRepository.save(config));
        } else {
            List<TicketPriceConfig> all = ticketPriceConfigRepository.findAll();
            Optional<TicketPriceConfig> existing = all.stream()
                .filter(c -> c.getDayType().equals(req.getDayType())
                          && c.getSlotName().equals(req.getSlotName())
                          && java.util.Objects.equals(c.getMovieId(), req.getMovieId()))
                .findFirst();
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                    "Cấu hình cho loại ngày/ngày '" + req.getDayType() + "', khung giờ '" + req.getSlotName() +
                    "' và " + (req.getMovieId() != null ? "phim này" : "mọi phim") + " đã tồn tại!"));
            }
            return ResponseEntity.ok(ticketPriceConfigRepository.save(req));
        }
    }

    @DeleteMapping("/configs/base/{id}")
    public ResponseEntity<?> deleteBasePrice(@PathVariable("id") Long id) {
        try {
            ticketPriceConfigRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể xóa cấu hình này!"));
        }
    }

    @PostMapping("/configs/seats")
    public ResponseEntity<?> updateSeatSurcharge(@RequestBody SeatTypeSurcharge req) {
        if (req.getId() != null) {
            Optional<SeatTypeSurcharge> surchargeOpt = seatTypeSurchargeRepository.findById(req.getId());
            if (!surchargeOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
            }
            SeatTypeSurcharge surcharge = surchargeOpt.get();
            surcharge.setSeatTypeCode(req.getSeatTypeCode());
            surcharge.setSurchargeAmount(req.getSurchargeAmount());
            return ResponseEntity.ok(seatTypeSurchargeRepository.save(surcharge));
        } else {
            Optional<SeatTypeSurcharge> existing = seatTypeSurchargeRepository.findBySeatTypeCode(req.getSeatTypeCode());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Mã loại ghế '" + req.getSeatTypeCode() + "' đã có cấu hình phụ thu!"));
            }
            return ResponseEntity.ok(seatTypeSurchargeRepository.save(req));
        }
    }

    @DeleteMapping("/configs/seats/{id}")
    public ResponseEntity<?> deleteSeatSurcharge(@PathVariable("id") Long id) {
        try {
            seatTypeSurchargeRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể xóa phụ thu loại ghế này!"));
        }
    }

    @PostMapping("/configs/formats")
    public ResponseEntity<?> updateFormatSurcharge(@RequestBody FormatSurcharge req) {
        if (req.getId() != null) {
            Optional<FormatSurcharge> surchargeOpt = formatSurchargeRepository.findById(req.getId());
            if (!surchargeOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
            }
            FormatSurcharge surcharge = surchargeOpt.get();
            surcharge.setFormatCode(req.getFormatCode());
            surcharge.setSurchargeAmount(req.getSurchargeAmount());
            return ResponseEntity.ok(formatSurchargeRepository.save(surcharge));
        } else {
            Optional<FormatSurcharge> existing = formatSurchargeRepository.findByFormatCode(req.getFormatCode());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Mã định dạng '" + req.getFormatCode() + "' đã có cấu hình phụ thu!"));
            }
            return ResponseEntity.ok(formatSurchargeRepository.save(req));
        }
    }

    @DeleteMapping("/configs/formats/{id}")
    public ResponseEntity<?> deleteFormatSurcharge(@PathVariable("id") Long id) {
        try {
            formatSurchargeRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể xóa phụ thu định dạng này!"));
        }
    }

    @PostMapping("/configs/discounts")
    public ResponseEntity<?> updateCustomerDiscount(@RequestBody CustomerDiscount req) {
        if (req.getId() != null) {
            Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findById(req.getId());
            if (!discountOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình!"));
            }
            CustomerDiscount discount = discountOpt.get();
            discount.setCustomerType(req.getCustomerType());
            discount.setDiscountRate(req.getDiscountRate());
            discount.setFixedPriceWeekday(req.getFixedPriceWeekday());
            discount.setMinPriceToApply(req.getMinPriceToApply());
            discount.setMaxDiscountAmount(req.getMaxDiscountAmount());
            return ResponseEntity.ok(customerDiscountRepository.save(discount));
        } else {
            Optional<CustomerDiscount> existing = customerDiscountRepository.findByCustomerType(req.getCustomerType());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Đối tượng '" + req.getCustomerType() + "' đã có cấu hình chiết khấu!"));
            }
            return ResponseEntity.ok(customerDiscountRepository.save(req));
        }
    }

    @DeleteMapping("/configs/discounts/{id}")
    public ResponseEntity<?> deleteCustomerDiscount(@PathVariable("id") Long id) {
        try {
            customerDiscountRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể xóa chiết khấu đối tượng này!"));
        }
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

    @PostMapping
    public ResponseEntity<?> createTicket(@RequestBody Map<String, Object> payload) {
        try {
            Long showtimeId = Long.valueOf(payload.get("showtimeId").toString());
            Long seatId = Long.valueOf(payload.get("seatId").toString());
            String customerType = payload.get("customerType").toString();
            String customerName = payload.get("customerName") != null ? payload.get("customerName").toString() : null;
            String customerPhone = payload.get("customerPhone") != null ? payload.get("customerPhone").toString() : null;
            String status = payload.get("status") != null ? payload.get("status").toString() : "BOOKED";

            return ResponseEntity.ok(ticketService.createTicket(showtimeId, seatId, customerType, customerName, customerPhone, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTicket(@PathVariable("id") Long id, @RequestBody Map<String, Object> payload) {
        try {
            Long showtimeId = Long.valueOf(payload.get("showtimeId").toString());
            Long seatId = Long.valueOf(payload.get("seatId").toString());
            String customerType = payload.get("customerType").toString();
            String customerName = payload.get("customerName") != null ? payload.get("customerName").toString() : null;
            String customerPhone = payload.get("customerPhone") != null ? payload.get("customerPhone").toString() : null;
            String status = payload.get("status") != null ? payload.get("status").toString() : "BOOKED";

            return ResponseEntity.ok(ticketService.updateTicket(id, showtimeId, seatId, customerType, customerName, customerPhone, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
