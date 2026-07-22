/**
 * Service tính toán Tổng đơn đặt vé cho Khách hàng Online, Áp dụng Voucher ví cá nhân và Khởi tạo Booking PENDING (`CustomerBookingService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CustomerBookingController` (Giao diện đặt vé 3 bước chọn ghế -> chọn bắp nước -> áp voucher -> thanh toán).
 * - Tương tác với:
 *   + `ComboRepository`: Tra cứu combo nước ép, bắp rang bơ (`getActiveCombos`).
 *   + `BookingTicketRepository`: Đổi trạng thái giữ ghế `HOLDING` gắn với `bookingId`.
 *   + `BookingRepository`, `BookingComboRepository`: Tạo bản ghi phiếu đặt vé PENDING chờ thanh toán Online (VNPay / payOS / Momo).
 *   + `VoucherRepository`: Tra cứu danh sách voucher trong Ví người dùng (`findWalletVouchers`), kiểm tra điều kiện áp dụng voucher cho tổng hóa đơn (`evaluateWalletVoucher`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final VoucherRepository voucherRepository;
    private final JdbcTemplate jdbcTemplate;

    public CustomerBookingService(ComboRepository comboRepository,
                                  BookingTicketRepository ticketRepository,
                                  BookingRepository bookingRepository,
                                  BookingComboRepository bookingComboRepository,
                                  ShowtimeRepository showtimeRepository,
                                  VoucherRepository voucherRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.comboRepository = comboRepository;
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
        this.bookingComboRepository = bookingComboRepository;
        this.showtimeRepository = showtimeRepository;
        this.voucherRepository = voucherRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lấy danh sách các sản phẩm Combo bắp nước đang hoạt động. */
    public List<Combo> getActiveCombos() {
        return comboRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_COMBO_STATUSES));
    }

    /** Kiểm tra số lượng mua từng loại combo hợp lệ (từ 0 đến 10 combo). */
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

    /** Tính tổng cộng thanh toán xem trước với mã voucher trực tiếp. */
    @Transactional(readOnly = true)
    public BookingSummary calculateSummary(BookingSelection selection, String holdToken,
                                           Map<Long, Integer> selectedCombos, String voucherCode) {
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos);
        String normalizedVoucher = normalizeVoucher(voucherCode);
        VoucherRule voucherRule = normalizedVoucher == null ? null : voucherRule(normalizedVoucher);
        BigDecimal discount = voucherRule == null ? BigDecimal.ZERO
                : base.beforeDiscount().multiply(voucherRule.discountPercent().divide(new BigDecimal("100"), 4, RoundingMode.DOWN))
                .setScale(0, RoundingMode.DOWN)
                .min(voucherRule.maxDiscount());
        return base.toSummary(discount, normalizedVoucher);
    }

    /** Tính tổng cộng thanh toán xem trước khi áp dụng mã voucher lưu sẵn trong Ví cá nhân khách hàng. */
    @Transactional(readOnly = true)
    public BookingSummary calculateSummaryWithWalletVoucher(Integer accountId, BookingSelection selection,
                                                            String holdToken,
                                                            Map<Long, Integer> selectedCombos,
                                                            Long voucherId) {
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos);
        WalletVoucherOption voucherOption = voucherId == null
                ? null
                : evaluateWalletVoucher(accountId, voucherId, base, true);
        BigDecimal discount = voucherOption == null ? BigDecimal.ZERO : voucherOption.discount();
        String voucherCode = voucherOption == null ? null : voucherOption.voucher().getCode();
        return base.toSummary(discount, voucherCode);
    }

    /** Lấy danh sách danh mục các Voucher trong Ví tài khoản kèm theo trạng thái đủ điều kiện hay không. */
    @Transactional(readOnly = true)
    public List<WalletVoucherOption> getWalletVoucherOptions(Integer accountId, BookingSelection selection,
                                                             String holdToken,
                                                             Map<Long, Integer> selectedCombos) {
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos);
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findWalletVouchers(requireAccountId(accountId)).stream()
                .filter(voucher -> !Boolean.TRUE.equals(voucher.getIsDeleted()))
                .filter(voucher -> voucher.getEndDate() != null && voucher.getEndDate().isAfter(now))
                .map(voucher -> evaluateWalletVoucher(accountId, voucher, base, false))
                .toList();
    }

    /** Tạo đơn hàng Booking ở trạng thái `PENDING` (chờ chuyển trang cổng thanh toán Online). */
    @Transactional
    public Booking createPendingBooking(Integer accountId, BookingSelection selection, String holdToken,
                                        Map<Long, Integer> selectedCombos, String voucherCode) {
        BookingSummary summary = calculateSummary(selection, holdToken, selectedCombos, voucherCode);
        return savePendingBooking(accountId, selection, summary);
    }

    /** Tạo đơn hàng Booking ở trạng thái `PENDING` áp dụng mã voucher trong ví cá nhân. */
    @Transactional
    public Booking createPendingBookingWithWalletVoucher(Integer accountId, BookingSelection selection, String holdToken,
                                                         Map<Long, Integer> selectedCombos, Long voucherId) {
        BookingSummary summary = calculateSummaryWithWalletVoucher(accountId, selection, holdToken, selectedCombos, voucherId);
        return savePendingBooking(accountId, selection, summary);
    }

    private Booking savePendingBooking(Integer accountId, BookingSelection selection, BookingSummary summary) {
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

    /** Tra cứu toàn bộ chi tiết suất chiếu, danh sách vé và combo của một BookingId. */
    @Transactional(readOnly = true)
    public BookingDetails getBookingDetails(Long bookingId, Integer accountId) {
        Booking booking = bookingRepository.findByIdAndAccountId(bookingId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        return getBookingDetails(booking);
    }

    /** Tra cứu chi tiết đơn hàng Booking không phân biệt tài khoản. */
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

    private SummaryBase buildSummaryBase(BookingSelection selection, String holdToken,
                                         Map<Long, Integer> selectedCombos) {
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

        LocalDateTime expiresAt = tickets.stream()
                .map(BookingTicket::getHoldExpiresAt)
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        return new SummaryBase(selection, tickets, comboLines, ticketSubtotal, comboSubtotal,
                ticketSubtotal.add(comboSubtotal), expiresAt);
    }

    private WalletVoucherOption evaluateWalletVoucher(Integer accountId, Long voucherId,
                                                      SummaryBase base, boolean strict) {
        Voucher voucher = voucherRepository.findWalletVoucher(requireAccountId(accountId), voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher này chưa được lưu trong ví của bạn."));
        return evaluateWalletVoucher(accountId, voucher, base, strict);
    }

    private WalletVoucherOption evaluateWalletVoucher(Integer accountId, Voucher voucher,
                                                      SummaryBase base, boolean strict) {
        String reason = voucherIneligibilityReason(accountId, voucher, base);
        BigDecimal eligibleAmount = eligibleVoucherAmount(voucher, base);
        BigDecimal discount = reason == null ? calculateVoucherDiscount(voucher, eligibleAmount) : BigDecimal.ZERO;
        if (reason == null && discount.compareTo(BigDecimal.ZERO) <= 0) {
            reason = "Voucher chưa tạo ra giá trị giảm cho đơn hàng hiện tại.";
        }
        if (strict && reason != null) {
            throw new IllegalArgumentException(reason);
        }
        return new WalletVoucherOption(voucher, discount, eligibleAmount, reason == null, reason);
    }

    private String voucherIneligibilityReason(Integer accountId, Voucher voucher, SummaryBase base) {
        requireAccountId(accountId);
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(voucher.getIsDeleted())) {
            return "Voucher này đã ngừng hoạt động.";
        }
        if (voucher.getStartDate() == null || voucher.getEndDate() == null) {
            return "Voucher thiếu thời gian áp dụng.";
        }
        if (voucher.getStartDate().isAfter(now)) {
            return "Voucher chưa đến thời gian áp dụng.";
        }
        if (!voucher.getEndDate().isAfter(now)) {
            return "Voucher đã hết hạn.";
        }
        if (voucher.getTotalQuantity() != null && voucher.getUsedQuantity() != null
                && voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            return "Voucher đã hết số lượng phát hành.";
        }
        BigDecimal minOrderValue = safeMoney(voucher.getMinOrderValue());
        if (base.beforeDiscount().compareTo(minOrderValue) < 0) {
            return "Đơn hàng chưa đạt tối thiểu "
                    + minOrderValue.setScale(0, RoundingMode.DOWN).toPlainString() + "đ.";
        }
        if (!matchesApplicableDay(voucher.getApplicableDays(), base.selection().showDate())) {
            return "Voucher không áp dụng cho ngày chiếu đã chọn.";
        }
        if (Boolean.FALSE.equals(voucher.getIsHolidayApplicable()) && isKnownHoliday(base.selection().showDate())) {
            return "Voucher không áp dụng vào ngày lễ.";
        }
        if (eligibleVoucherAmount(voucher, base).compareTo(BigDecimal.ZERO) <= 0) {
            return "Đơn hàng hiện tại không có dịch vụ phù hợp với voucher.";
        }
        int limitPerUser = voucher.getLimitPerUser() == null ? 1 : voucher.getLimitPerUser();
        if (limitPerUser > 0 && voucher.getCode() != null) {
            long usedByAccount = bookingRepository.countByAccountIdAndVoucherCodeAndStatusIn(
                    accountId,
                    voucher.getCode(),
                    List.of(Booking.Status.PENDING, Booking.Status.PAID)
            );
            if (usedByAccount >= limitPerUser) {
                return "Bạn đã dùng đủ số lượt cho voucher này.";
            }
        }
        if (voucher.getDiscountType() == null || voucher.getDiscountValue() == null
                || voucher.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            return "Voucher chưa có cấu hình giảm giá hợp lệ.";
        }
        return null;
    }

    private BigDecimal calculateVoucherDiscount(Voucher voucher, BigDecimal eligibleAmount) {
        BigDecimal discount;
        if (voucher.getDiscountType() == Voucher.DiscountType.PERCENTAGE) {
            discount = eligibleAmount
                    .multiply(voucher.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.DOWN))
                    .setScale(0, RoundingMode.DOWN);
            BigDecimal maxDiscount = voucher.getMaxDiscountAmount();
            if (maxDiscount != null && maxDiscount.compareTo(BigDecimal.ZERO) > 0) {
                discount = discount.min(maxDiscount);
            }
        } else {
            discount = voucher.getDiscountValue().setScale(0, RoundingMode.DOWN);
        }
        return discount.min(eligibleAmount).max(BigDecimal.ZERO);
    }

    private BigDecimal eligibleVoucherAmount(Voucher voucher, SummaryBase base) {
        Voucher.ServiceScope scope = voucher.getServiceScope();
        if (scope == Voucher.ServiceScope.WATER) {
            return base.comboSubtotal();
        }
        BigDecimal ticketAmount = eligibleTicketAmount(voucher, base);
        if (scope == Voucher.ServiceScope.TICKET) {
            return ticketAmount;
        }
        return ticketAmount.add(base.comboSubtotal());
    }

    private BigDecimal eligibleTicketAmount(Voucher voucher, SummaryBase base) {
        Set<String> applicableSeatTypes = normalizedSeatTypes(voucher.getApplicableSeats());
        if (applicableSeatTypes.isEmpty()) {
            return base.ticketSubtotal();
        }
        return base.tickets().stream()
                .filter(ticket -> applicableSeatTypes.contains(normalizeSeatType(ticket.getSeatType())))
                .map(BookingTicket::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Set<String> normalizedSeatTypes(String applicableSeats) {
        if (applicableSeats == null || applicableSeats.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(applicableSeats.split("[,;|]"))
                .map(this::normalizeSeatType)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private String normalizeSeatType(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "normal", "standard", "std", "thuong", "thường", "ghe thuong", "ghế thường" -> "std";
            case "double", "couple", "doi", "đôi", "ghe doi", "ghế đôi" -> "couple";
            default -> normalized;
        };
    }

    private boolean matchesApplicableDay(Voucher.ApplicableDay applicableDay, LocalDate showDate) {
        if (applicableDay == null || applicableDay == Voucher.ApplicableDay.ALL || showDate == null) {
            return true;
        }
        DayOfWeek day = showDate.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        return applicableDay == Voucher.ApplicableDay.WEEKEND ? weekend : !weekend;
    }

    private boolean isKnownHoliday(LocalDate date) {
        if (date == null) return false;
        return (date.getMonthValue() == 1 && date.getDayOfMonth() == 1)
                || (date.getMonthValue() == 4 && date.getDayOfMonth() == 30)
                || (date.getMonthValue() == 5 && date.getDayOfMonth() == 1)
                || (date.getMonthValue() == 9 && date.getDayOfMonth() == 2);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int requireAccountId(Integer accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để sử dụng voucher trong ví.");
        }
        return accountId;
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
    public record WalletVoucherOption(Voucher voucher, BigDecimal discount, BigDecimal eligibleAmount,
                                      boolean eligible, String reason) { }
    public record BookingSummary(BookingSelection selection, List<BookingTicket> tickets, List<ComboLine> combos,
                                 BigDecimal ticketSubtotal, BigDecimal comboSubtotal, BigDecimal discount,
                                 BigDecimal total, String voucherCode, LocalDateTime expiresAt) { }
    public record BookingDetails(Booking booking, Showtime showtime,
                                 List<BookingTicket> tickets, List<BookingCombo> combos) { }
    private record SummaryBase(BookingSelection selection, List<BookingTicket> tickets, List<ComboLine> combos,
                               BigDecimal ticketSubtotal, BigDecimal comboSubtotal, BigDecimal beforeDiscount,
                               LocalDateTime expiresAt) {
        BookingSummary toSummary(BigDecimal discount, String voucherCode) {
            BigDecimal safeDiscount = discount == null ? BigDecimal.ZERO : discount;
            return new BookingSummary(selection, tickets, combos, ticketSubtotal, comboSubtotal,
                    safeDiscount, beforeDiscount.subtract(safeDiscount), voucherCode, expiresAt);
        }
    }
    private record VoucherRule(BigDecimal discountPercent, BigDecimal maxDiscount) { }
}

