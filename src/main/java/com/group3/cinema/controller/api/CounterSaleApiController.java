package com.group3.cinema.controller.api;

import com.group3.cinema.service.CounterSaleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/counter-sales")
public class CounterSaleApiController {

    private final CounterSaleService counterSaleService;

    public CounterSaleApiController(CounterSaleService counterSaleService) {
        this.counterSaleService = counterSaleService;
    }

    @GetMapping("/showtimes")
    public ResponseEntity<?> showtimes(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "movieId", required = false) Integer movieId) {
        return handle(() -> counterSaleService.getSellableShowtimes(date, movieId));
    }

    @GetMapping("/customers")
    public ResponseEntity<?> customers(@RequestParam(value = "keyword", required = false) String keyword) {
        return handle(() -> counterSaleService.searchCustomers(keyword));
    }

    @GetMapping("/combos")
    public ResponseEntity<?> combos() {
        return handle(counterSaleService::getActiveCombos);
    }

    @GetMapping("/vouchers")
    public ResponseEntity<?> vouchers() {
        return handle(counterSaleService::getActiveVouchers);
    }

    @GetMapping("/seats")
    public ResponseEntity<?> seats(@RequestParam("showtimeId") Long showtimeId,
                                   @RequestParam(value = "holdToken", required = false) String holdToken) {
        return handle(() -> counterSaleService.getSeatMap(showtimeId, holdToken));
    }

    @PostMapping("/hold")
    public ResponseEntity<?> hold(@RequestBody CounterSaleService.HoldRequest request) {
        return handle(() -> counterSaleService.holdSeats(request));
    }

    @PostMapping("/release")
    public ResponseEntity<?> release(@RequestBody Map<String, String> payload) {
        return handle(() -> {
            counterSaleService.releaseHold(payload.get("holdToken"));
            return Map.of("success", true);
        });
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody CounterSaleService.CounterSaleRequest request) {
        return handle(() -> counterSaleService.previewSale(request));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(@RequestBody CounterSaleService.CounterSaleRequest request) {
        return handle(() -> counterSaleService.completeSale(request));
    }

    @PostMapping("/payment-link")
    public ResponseEntity<?> paymentLink(@RequestBody CounterSaleService.CounterSaleRequest request,
                                         HttpServletRequest httpRequest) {
        return handle(() -> counterSaleService.createCounterPayment(request, httpRequest));
    }

    private ResponseEntity<?> handle(Action action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi hệ thống: " + ex.getMessage(), "message", "Lỗi hệ thống: " + ex.getMessage()));
        }
    }

    private interface Action {
        Object run();
    }
}
