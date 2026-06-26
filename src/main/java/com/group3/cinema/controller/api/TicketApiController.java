/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: TicketApiController.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Định nghĩa các REST API phục vụ cho quản lý vé, thống kê suất chiếu và điều chỉnh cấu hình giá vé.
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
     * Lấy danh sách vé của một suất chiếu.
     */
    @GetMapping("/showtime/{showtimeId}")
    public ResponseEntity<List<Ticket>> getTicketsByShowtime(@PathVariable("showtimeId") Long showtimeId) {
        return ResponseEntity.ok(ticketService.getTicketsByShowtime(showtimeId));
    }

    /**
     * Lấy thống kê của một suất chiếu.
     */
    @GetMapping("/stats/{showtimeId}")
    public ResponseEntity<Map<String, Object>> getShowtimeStats(@PathVariable("showtimeId") Long showtimeId) {
        return ResponseEntity.ok(ticketService.getShowtimeStats(showtimeId));
    }

    /**
     * Đổi trạng thái vé nhanh (Bán vé / Hủy vé của Người lớn).
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> toggleTicketStatus(@PathVariable("id") Long id) {
        try {
            Ticket updated = ticketService.toggleTicketStatus(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Bán vé chọn đối tượng khách hàng (Student, Child, Elderly...).
     */
    @PostMapping("/{id}/sell")
    public ResponseEntity<?> sellTicket(@PathVariable("id") Long id, @RequestParam("customerType") String customerType) {
        try {
            Ticket updated = ticketService.sellTicket(id, customerType);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cập nhật đối tượng khách hàng của vé đã bán, tính lại giá vé theo đối tượng mới.
     */
    @PutMapping("/{id}/update-customer")
    public ResponseEntity<?> updateCustomerType(@PathVariable("id") Long id, @RequestParam("customerType") String customerType) {
        try {
            Ticket updated = ticketService.updateCustomerType(id, customerType);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    //   CÁC ENDPOINT CẤU HÌNH GIÁ VÉ (PRICING MATRIX CONFIG)
    // =========================================================================

    /**
     * Lấy toàn bộ các cấu hình giá vé hiện tại.
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getPricingConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("basePrices", ticketPriceConfigRepository.findAll());
        configs.put("seatSurcharges", seatTypeSurchargeRepository.findAll());
        configs.put("formatSurcharges", formatSurchargeRepository.findAll());
        configs.put("customerDiscounts", customerDiscountRepository.findAll());
        return ResponseEntity.ok(configs);
    }

    /**
     * Cập nhật Giá Cơ Bản cho loại ngày + khung giờ.
     */
    @PostMapping("/configs/base")
    public ResponseEntity<?> updateBasePrice(@RequestBody TicketPriceConfig req) {
        Optional<TicketPriceConfig> configOpt = ticketPriceConfigRepository.findById(req.getId());
        if (!configOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình giá cơ bản!"));
        }
        TicketPriceConfig config = configOpt.get();
        config.setBasePrice(req.getBasePrice());
        return ResponseEntity.ok(ticketPriceConfigRepository.save(config));
    }

    /**
     * Cập nhật phụ phí loại ghế.
     */
    @PostMapping("/configs/seats")
    public ResponseEntity<?> updateSeatSurcharge(@RequestBody SeatTypeSurcharge req) {
        Optional<SeatTypeSurcharge> surchargeOpt = seatTypeSurchargeRepository.findById(req.getId());
        if (!surchargeOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình phụ phí ghế!"));
        }
        SeatTypeSurcharge surcharge = surchargeOpt.get();
        surcharge.setSurchargeAmount(req.getSurchargeAmount());
        return ResponseEntity.ok(seatTypeSurchargeRepository.save(surcharge));
    }

    /**
     * Cập nhật phụ phí định dạng phòng chiếu.
     */
    @PostMapping("/configs/formats")
    public ResponseEntity<?> updateFormatSurcharge(@RequestBody FormatSurcharge req) {
        Optional<FormatSurcharge> surchargeOpt = formatSurchargeRepository.findById(req.getId());
        if (!surchargeOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình phụ phí định dạng!"));
        }
        FormatSurcharge surcharge = surchargeOpt.get();
        surcharge.setSurchargeAmount(req.getSurchargeAmount());
        return ResponseEntity.ok(formatSurchargeRepository.save(surcharge));
    }

    /**
     * Cập nhật chiết khấu đối tượng khách hàng.
     */
    @PostMapping("/configs/discounts")
    public ResponseEntity<?> updateCustomerDiscount(@RequestBody CustomerDiscount req) {
        Optional<CustomerDiscount> discountOpt = customerDiscountRepository.findById(req.getId());
        if (!discountOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy cấu hình chiết khấu!"));
        }
        CustomerDiscount discount = discountOpt.get();
        discount.setDiscountRate(req.getDiscountRate());
        discount.setFixedPriceWeekday(req.getFixedPriceWeekday());
        return ResponseEntity.ok(customerDiscountRepository.save(discount));
    }
}
