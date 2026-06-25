package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Customer booking summary, combo, voucher, and booking creation logic.
 * Updated on 2026-06-26: Voucher data is loaded from SQL table booking_vouchers.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerBookingService {
    private static final Set<String> ACTIVE_COMBO_STATUSES = Set.of("ACTIVE", "NEW");

    private final ComboRepository comboRepository;
    private final BookingTicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final BookingComboRepository bookingComboRepository;
    private final ShowtimeRepository showtimeRepository;
    private final JdbcTemplate jdbcTemplate;

    public CustomerBookingService(ComboRepository comboRepository,
                                  BookingTicketRepository ticketRepository,
                                  BookingRepository bookingRepository,
                                  BookingComboRepository bookingComboRepository,
                                  ShowtimeRepository showtimeRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.comboRepository = comboRepository;
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
        this.bookingComboRepository = bookingComboRepository;
        this.showtimeRepository = showtimeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Combo> getActiveCombos() {
        return comboRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_COMBO_STATUSES));
    }

    public LinkedHashMap<Long, Integer> validateComboQuantities(Map<String, String> params) {
        LinkedHashMap<Long, Integer> selected = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().startsWith("combo_")) continue;
            try {
                long comboId = Long.parseLong(entry.getKey().substring(6));
                int quantity = Integer.parseInt(entry.getValue());
                if (quantity < 0 || quantity > 10) {
                    throw new IllegalArgumentException("Số lượng combo phải từ 0 đến 10.");
                }
                if (quantity > 0) selected.put(comboId, quantity);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Số lượng combo không hợp lệ.");
            }
        }
        List<Combo> combos = comboRepository.findAllById(selected.keySet());
        if (combos.size() != selected.size()
                || combos.stream().anyMatch(combo -> !ACTIVE_COMBO_STATUSES.contains(combo.getStatus()))) {
            throw new IllegalArgumentException("Một combo đã ngừng bán. Vui lòng chọn lại.");
        }
        return selected;
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummary(BookingSelection selection, String holdToken,
                                           Map<Long, Integer> selectedCombos, String voucherCode) {
        List<BookingTicket> tickets = requireValidHolds(selection, holdToken);
        BigDecimal ticketSubtotal = tickets.stream().map(BookingTicket::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ComboLine> comboLines = new ArrayList<>();
        BigDecimal comboSubtotal = BigDecimal.ZERO;

        if (selectedCombos != null && !selectedCombos.isEmpty()) {
            Map<Long, Combo> comboMap = new HashMap<>();
            comboRepository.findAllById(selectedCombos.keySet()).forEach(combo -> comboMap.put(combo.getId(), combo));
            for (Map.Entry<Long, Integer> entry : selectedCombos.entrySet()) {
                Combo combo = comboMap.get(entry.getKey());
                if (combo == null || !ACTIVE_COMBO_STATUSES.contains(combo.getStatus()) || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Combo đã chọn không còn khả dụng.");
                }
                BigDecimal subtotal = combo.getPrice().multiply(BigDecimal.valueOf(entry.getValue()));
                comboLines.add(new ComboLine(combo.getId(), combo.getName(), entry.getValue(), combo.getPrice(), subtotal));
                comboSubtotal = comboSubtotal.add(subtotal);
            }
        }

        String normalizedVoucher = normalizeVoucher(voucherCode);
        BigDecimal beforeDiscount = ticketSubtotal.add(comboSubtotal);
        VoucherRule voucherRule = normalizedVoucher == null ? null : voucherRule(normalizedVoucher);
        BigDecimal discount = voucherRule == null ? BigDecimal.ZERO
                : beforeDiscount.multiply(voucherRule.discountPercent().divide(new BigDecimal("100"), 4, RoundingMode.DOWN))
                .setScale(0, RoundingMode.DOWN)
                .min(voucherRule.maxDiscount());

        return new BookingSummary(selection, tickets, comboLines, ticketSubtotal, comboSubtotal,
                discount, beforeDiscount.subtract(discount), normalizedVoucher,
                tickets.stream().map(BookingTicket::getHoldExpiresAt).min(LocalDateTime::compareTo).orElseThrow());
    }

    @Transactional
    public Booking createPendingBooking(Integer accountId, BookingSelection selection, String holdToken,
                                        Map<Long, Integer> selectedCombos, String voucherCode) {
        BookingSummary summary = calculateSummary(selection, holdToken, selectedCombos, voucherCode);
        LocalDateTime paymentExpiry = LocalDateTime.now().plusMinutes(5);
        Booking booking = new Booking();
        booking.setAccountId(accountId);
        booking.setShowtimeId(selection.showtimeId());
        booking.setStatus(Booking.Status.PENDING);
        booking.setTicketSubtotal(summary.ticketSubtotal());
        booking.setComboSubtotal(summary.comboSubtotal());
        booking.setDiscountAmount(summary.discount());
        booking.setTotalAmount(summary.total());
        booking.setVoucherCode(summary.voucherCode());
        booking.setCreatedAt(LocalDateTime.now());
        booking.setExpiresAt(paymentExpiry);
        booking = bookingRepository.save(booking);

        for (BookingTicket ticket : summary.tickets()) {
            ticket.setBookingId(booking.getId());
            ticket.setHoldExpiresAt(paymentExpiry);
        }
        ticketRepository.saveAll(summary.tickets());
        for (ComboLine line : summary.combos()) {
            BookingCombo item = new BookingCombo();
            item.setBookingId(booking.getId());
            item.setComboId(line.id());
            item.setComboName(line.name());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.subtotal());
            bookingComboRepository.save(item);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public BookingDetails getBookingDetails(Long bookingId, Integer accountId) {
        Booking booking = bookingRepository.findByIdAndAccountId(bookingId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        return getBookingDetails(booking);
    }

    @Transactional(readOnly = true)
    public BookingDetails getBookingDetails(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        return getBookingDetails(booking);
    }

    private BookingDetails getBookingDetails(Booking booking) {
        Showtime showtime = showtimeRepository.findById(booking.getShowtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu của đơn vé."));
        return new BookingDetails(booking, showtime, ticketRepository.findByBookingId(booking.getId()),
                bookingComboRepository.findByBookingId(booking.getId()));
    }

    private List<BookingTicket> requireValidHolds(BookingSelection selection, String holdToken) {
        if (holdToken == null || holdToken.isBlank()) {
            throw new IllegalArgumentException("Phiên giữ ghế không tồn tại.");
        }
        List<BookingTicket> tickets = ticketRepository.findByHoldToken(holdToken).stream()
                .filter(ticket -> selection.showtimeId().equals(ticket.getShowtimeId()))
                .toList();
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa chọn ghế hoặc thời gian giữ ghế đã hết.");
        }
        if (tickets.stream().anyMatch(ticket -> ticket.getStatus() != BookingTicket.Status.HOLDING
                || ticket.getHoldExpiresAt() == null || ticket.getHoldExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new IllegalArgumentException("Thời gian giữ ghế đã hết. Vui lòng chọn lại ghế.");
        }
        return tickets;
    }

    private String normalizeVoucher(String code) {
        if (code == null || code.isBlank()) return null;
        return code.trim().toUpperCase();
    }

    private VoucherRule voucherRule(String code) {
        List<VoucherRule> rules = jdbcTemplate.query(
                "SELECT discount_percent, max_discount FROM booking_vouchers WHERE code = ? AND active = 1",
                (rs, rowNum) -> new VoucherRule(rs.getBigDecimal("discount_percent"), rs.getBigDecimal("max_discount")),
                code
        );
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Mã voucher không hợp lệ hoặc đã hết hạn.");
        }
        return rules.get(0);
    }

    public record ComboLine(Long id, String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) { }
    public record BookingSummary(BookingSelection selection, List<BookingTicket> tickets, List<ComboLine> combos,
                                 BigDecimal ticketSubtotal, BigDecimal comboSubtotal, BigDecimal discount,
                                 BigDecimal total, String voucherCode, LocalDateTime expiresAt) { }
    public record BookingDetails(Booking booking, Showtime showtime,
                                 List<BookingTicket> tickets, List<BookingCombo> combos) { }
    private record VoucherRule(BigDecimal discountPercent, BigDecimal maxDiscount) { }
}
