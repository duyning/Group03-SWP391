package com.group3.cinema.controller;

/*
 * Added on 2026-06-24: Customer booking navigation from showtime to payment.
 * Updated on 2026-06-26: Showtime page display values are loaded from SQL-backed services.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.dto.BookingSeatView;
import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.BookingShowtimeService;
import com.group3.cinema.service.CustomerBookingService;
import com.group3.cinema.service.SeatHoldingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Controller
@RequestMapping("/booking")
public class BookingController {

    public static final String BOOKING_SELECTION_SESSION_KEY = "bookingSelection";
    private static final String SELECTED_VOUCHER_ID_SESSION_KEY = "selectedVoucherId";
    private final BookingShowtimeService bookingShowtimeService;
    private final SeatHoldingService seatHoldingService;
    private final CustomerBookingService customerBookingService;

    public BookingController(BookingShowtimeService bookingShowtimeService,
                             SeatHoldingService seatHoldingService,
                             CustomerBookingService customerBookingService) {
        this.bookingShowtimeService = bookingShowtimeService;
        this.seatHoldingService = seatHoldingService;
        this.customerBookingService = customerBookingService;
    }

    @GetMapping("/showtimes")
    public String selectShowtime(@RequestParam("movieId") int movieId,
                                 @RequestParam(value = "date", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(value = "from", required = false) String from,
                                 HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        try {
            Movie movie = bookingShowtimeService.getBookableMovie(movieId);
            if ("wishlist".equalsIgnoreCase(from)) {
                session.setAttribute("from_wishlist_movie_" + movieId, true);
            } else {
                session.removeAttribute("from_wishlist_movie_" + movieId);
            }
            var schedule = bookingShowtimeService.getAvailableShowtimeSchedule(movieId);
            LocalDate firstShowDate = schedule.stream().findFirst()
                    .map(com.group3.cinema.dto.BookingShowtimeDateView::date)
                    .orElse(LocalDate.now());
            LocalDate selectedDate = date != null && schedule.stream().anyMatch(day -> day.date().equals(date))
                    ? date : firstShowDate;

            model.addAttribute("user", (Account) session.getAttribute("loggedInUser"));
            model.addAttribute("movie", movie);
            model.addAttribute("selectedDate", selectedDate);
            model.addAttribute("schedule", schedule);
            model.addAttribute("cinemaName", bookingShowtimeService.getCinemaName());
            return "booking-showtime";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/movies";
        }
    }

    @PostMapping("/showtimes/select")
    public String confirmShowtime(@RequestParam("showtimeId") long showtimeId,
                                  @RequestParam("movieId") int movieId,
                                  @RequestParam("date")
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            BookingSelection selection = bookingShowtimeService.validateAndCreateSelection(
                    showtimeId, movieId, date);
            seatHoldingService.releaseHold((String) session.getAttribute("seatHoldToken"));
            clearBookingSteps(session);
            session.setAttribute(BOOKING_SELECTION_SESSION_KEY, selection);
            return "redirect:/booking/seats";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/showtimes?movieId=" + movieId + "&date=" + date;
        }
    }

    @GetMapping("/seats")
    public String seatSelection(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);
        if (selection == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn suất chiếu trước khi chọn ghế.");
            return "redirect:/movies";
        }
        model.addAttribute("user", (Account) session.getAttribute("loggedInUser"));
        model.addAttribute("selection", selection);
        try {
            var seats = seatHoldingService.getSeatMap(selection, (String) session.getAttribute("seatHoldToken"));
            model.addAttribute("seats", seats);
            model.addAttribute("seatTypes", seatHoldingService.getActiveSeatTypes());
            model.addAttribute("roomCols", seats.stream()
                    .mapToInt(s -> s.colIndex() + Math.max(1, s.capacity()))
                    .max().orElse(10));
            model.addAttribute("holdExpiresAt", session.getAttribute("seatHoldExpiresAt"));
            return "seat-selection";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/showtimes?movieId=" + selection.movieId() + "&date=" + selection.showDate();
        }
    }

    @GetMapping("/seats/status")
    @ResponseBody
    public List<BookingSeatView> seatStatus(HttpSession session) {
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);
        if (selection == null) {
            throw new IllegalArgumentException("Vui lòng chọn suất chiếu trước.");
        }
        return seatHoldingService.getSeatMap(selection, (String) session.getAttribute("seatHoldToken"));
    }

    @PostMapping("/seats")
    public String holdSeats(@RequestParam(value = "seatIds", required = false) List<Long> seatIds,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        try {
            SeatHoldingService.HoldResult result = seatHoldingService.holdSeats(selection, seatIds,
                    (String) session.getAttribute("seatHoldToken"));
            session.setAttribute("seatHoldToken", result.token());
            session.setAttribute("seatHoldExpiresAt", result.expiresAt());
            session.removeAttribute("selectedCombos");
            clearSelectedVoucher(session);
            return "redirect:/booking/combos";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    @GetMapping("/seats/change")
    public String changeSeats(HttpSession session, RedirectAttributes redirectAttributes) {
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        seatHoldingService.releaseHold((String) session.getAttribute("seatHoldToken"));
        session.removeAttribute("seatHoldToken");
        session.removeAttribute("seatHoldExpiresAt");
        session.removeAttribute("selectedCombos");
        clearSelectedVoucher(session);
        redirectAttributes.addFlashAttribute("success", "Đã thả ghế cũ. Vui lòng chọn lại ghế.");
        return "redirect:/booking/seats";
    }

    @GetMapping("/combos")
    public String selectCombos(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        try {
            CustomerBookingService.BookingSummary summary = customerBookingService.calculateSummary(
                    selection, (String) session.getAttribute("seatHoldToken"), selectedCombos(session), null);
            model.addAttribute("user", session.getAttribute("loggedInUser"));
            model.addAttribute("selection", selection);
            model.addAttribute("combos", customerBookingService.getActiveCombos());
            model.addAttribute("selectedCombos", selectedCombos(session));
            model.addAttribute("summary", summary);
            return "booking-combo";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    @PostMapping("/combos")
    public String saveCombos(@RequestParam Map<String, String> params, HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            session.setAttribute("selectedCombos", customerBookingService.validateComboQuantities(params));
            clearSelectedVoucher(session);
            return "redirect:/booking/summary";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/combos";
        }
    }

    @GetMapping("/summary")
    public String summary(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        Account account = requireLoggedInAccount(session, redirectAttributes);
        if (account == null) return "redirect:/login";
        Long selectedVoucherId = selectedVoucherId(session);
        try {
            CustomerBookingService.BookingSummary summary = customerBookingService.calculateSummaryWithWalletVoucher(
                    account.getAccountID(), selection,
                    (String) session.getAttribute("seatHoldToken"), selectedCombos(session),
                    selectedVoucherId);
            model.addAttribute("user", account);
            model.addAttribute("summary", summary);
            model.addAttribute("voucherOptions", customerBookingService.getWalletVoucherOptions(
                    account.getAccountID(), selection, (String) session.getAttribute("seatHoldToken"),
                    selectedCombos(session)));
            model.addAttribute("selectedVoucherId", selectedVoucherId);
            return "booking-summary";
        } catch (IllegalArgumentException ex) {
            if (selectedVoucherId != null) {
                clearSelectedVoucher(session);
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/booking/summary";
            }
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    @PostMapping("/voucher")
    public String applyVoucher(@RequestParam(value = "voucherId", required = false) String voucherIdValue,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        Account account = requireLoggedInAccount(session, redirectAttributes);
        if (account == null) return "redirect:/login";
        try {
            Long voucherId = parseVoucherId(voucherIdValue);
            if (voucherId == null) {
                boolean hadVoucher = selectedVoucherId(session) != null;
                clearSelectedVoucher(session);
                redirectAttributes.addFlashAttribute("success",
                        hadVoucher ? "Đã bỏ voucher khỏi đơn." : "Đơn hàng sẽ không dùng voucher.");
                return "redirect:/booking/summary";
            }
            CustomerBookingService.BookingSummary summary = customerBookingService.calculateSummaryWithWalletVoucher(
                    account.getAccountID(), selection, (String) session.getAttribute("seatHoldToken"),
                    selectedCombos(session), voucherId);
            session.setAttribute(SELECTED_VOUCHER_ID_SESSION_KEY, voucherId);
            session.setAttribute("voucherCode", summary.voucherCode());
            redirectAttributes.addFlashAttribute("success", "Áp dụng voucher thành công.");
        } catch (IllegalArgumentException ex) {
            clearSelectedVoucher(session);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/booking/summary";
    }

    @PostMapping("/confirm")
    public String confirmBooking(HttpSession session, RedirectAttributes redirectAttributes) {
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để thanh toán.");
            return "redirect:/login";
        }
        try {
            var booking = customerBookingService.createPendingBookingWithWalletVoucher(account.getAccountID(), selection,
                    (String) session.getAttribute("seatHoldToken"), selectedCombos(session),
                    selectedVoucherId(session));
            session.setAttribute("bookingId", booking.getId());
            return "redirect:/payment?bookingId=" + booking.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    private BookingSelection requireSelection(HttpSession session, RedirectAttributes redirectAttributes) {
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);
        if (selection == null) redirectAttributes.addFlashAttribute("error", "Vui lòng chọn suất chiếu trước.");
        return selection;
    }

    private Account requireLoggedInAccount(HttpSession session, RedirectAttributes redirectAttributes) {
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tiếp tục đặt vé.");
        }
        return account;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Long, Integer> selectedCombos(HttpSession session) {
        Object value = session.getAttribute("selectedCombos");
        return value instanceof LinkedHashMap<?, ?> ? (LinkedHashMap<Long, Integer>) value : new LinkedHashMap<>();
    }

    private Long selectedVoucherId(HttpSession session) {
        Object value = session.getAttribute(SELECTED_VOUCHER_ID_SESSION_KEY);
        if (value instanceof Long id) return id;
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return parseVoucherId(text);
            } catch (IllegalArgumentException ex) {
                clearSelectedVoucher(session);
            }
        }
        return null;
    }

    private Long parseVoucherId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Voucher được chọn không hợp lệ.");
        }
    }

    private void clearSelectedVoucher(HttpSession session) {
        session.removeAttribute(SELECTED_VOUCHER_ID_SESSION_KEY);
        session.removeAttribute("voucherCode");
    }

    @PostMapping("/release-hold")
    @ResponseBody
    public Map<String, Object> releaseHold(HttpSession session) {
        String token = (String) session.getAttribute("seatHoldToken");
        if (token != null && !token.isBlank()) {
            seatHoldingService.releaseHold(token);
            session.removeAttribute("seatHoldToken");
            session.removeAttribute("seatHoldExpiresAt");
            // Không xóa BOOKING_SELECTION_SESSION_KEY ở đây vì có thể gây race condition
            // khi beacon được gửi đồng thời với form submit.
        }
        return Map.of("success", true);
    }

    private void clearBookingSteps(HttpSession session) {
        session.removeAttribute("seatHoldToken");
        session.removeAttribute("seatHoldExpiresAt");
        session.removeAttribute("selectedCombos");
        clearSelectedVoucher(session);
        session.removeAttribute("bookingId");
    }
}
