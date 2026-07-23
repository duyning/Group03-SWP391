package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Customer booking summary, combo, voucher, and booking creation logic.
 * Updated on 2026-06-26: Voucher data is loaded from SQL table booking_vouchers.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingFoodItem;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.FoodItem;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingFoodItemRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.FoodItemRepository;
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
    // Trạng thái NEW vẫn được coi là đang bán để tương thích dữ liệu combo/món vừa tạo.
    private static final Set<String> ACTIVE_COMBO_STATUSES = Set.of("ACTIVE", "NEW");
    private static final Set<String> ACTIVE_FOOD_STATUSES = Set.of("ACTIVE", "NEW");

    private final ComboRepository comboRepository;
    private final FoodItemRepository foodItemRepository;
    private final BookingTicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final BookingComboRepository bookingComboRepository;
    private final BookingFoodItemRepository bookingFoodItemRepository;
    private final ShowtimeRepository showtimeRepository;
    private final VoucherRepository voucherRepository;
    private final JdbcTemplate jdbcTemplate;

    public CustomerBookingService(ComboRepository comboRepository,
                                  FoodItemRepository foodItemRepository,
                                  BookingTicketRepository ticketRepository,
                                  BookingRepository bookingRepository,
                                  BookingComboRepository bookingComboRepository,
                                  BookingFoodItemRepository bookingFoodItemRepository,
                                  ShowtimeRepository showtimeRepository,
                                  VoucherRepository voucherRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.comboRepository = comboRepository;
        this.foodItemRepository = foodItemRepository;
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
        this.bookingComboRepository = bookingComboRepository;
        this.bookingFoodItemRepository = bookingFoodItemRepository;
        this.showtimeRepository = showtimeRepository;
        this.voucherRepository = voucherRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Combo> getActiveCombos() {
        return comboRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_COMBO_STATUSES));
    }

    public List<FoodItem> getActiveFoodItems() {
        return foodItemRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_FOOD_STATUSES));
    }

    public LinkedHashMap<Long, Integer> validateComboQuantities(Map<String, String> params) {
        /*
         * Form gửi input theo mẫu combo_{id}. Chỉ nhận số lượng 0–10, sau đó
         * đọc lại toàn bộ ID từ database để chặn việc sửa request chọn combo đã ngừng bán.
         */
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

    public LinkedHashMap<Long, Integer> validateFoodItemQuantities(Map<String, String> params) {
        // Món lẻ dùng tiền tố food_ nhưng có cùng nguyên tắc kiểm tra an toàn như combo.
        LinkedHashMap<Long, Integer> selected = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().startsWith("food_")) continue;
            try {
                long foodItemId = Long.parseLong(entry.getKey().substring(5));
                int quantity = Integer.parseInt(entry.getValue());
                if (quantity < 0 || quantity > 10) {
                    throw new IllegalArgumentException("Số lượng món lẻ phải từ 0 đến 10.");
                }
                if (quantity > 0) selected.put(foodItemId, quantity);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Số lượng món lẻ không hợp lệ.");
            }
        }
        List<FoodItem> foodItems = foodItemRepository.findAllById(selected.keySet());
        if (foodItems.size() != selected.size()
                || foodItems.stream().anyMatch(item -> !ACTIVE_FOOD_STATUSES.contains(item.getStatus()))) {
            throw new IllegalArgumentException("Một món lẻ đã ngừng bán. Vui lòng chọn lại.");
        }
        return selected;
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummary(BookingSelection selection, String holdToken,
                                           Map<Long, Integer> selectedCombos, String voucherCode) {
        return calculateSummary(selection, holdToken, selectedCombos, Map.of(), voucherCode);
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummary(BookingSelection selection, String holdToken,
                                           Map<Long, Integer> selectedCombos,
                                           Map<Long, Integer> selectedFoodItems, String voucherCode) {
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos, selectedFoodItems);
        String normalizedVoucher = normalizeVoucher(voucherCode);
        VoucherRule voucherRule = normalizedVoucher == null ? null : voucherRule(normalizedVoucher);
        BigDecimal discount = voucherRule == null ? BigDecimal.ZERO
                : base.beforeDiscount().multiply(voucherRule.discountPercent().divide(new BigDecimal("100"), 4, RoundingMode.DOWN))
                .setScale(0, RoundingMode.DOWN)
                .min(voucherRule.maxDiscount());
        return base.toSummary(discount, normalizedVoucher);
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummaryWithWalletVoucher(Integer accountId, BookingSelection selection,
                                                            String holdToken,
                                                            Map<Long, Integer> selectedCombos,
                                                            Long voucherId) {
        return calculateSummaryWithWalletVoucher(accountId, selection, holdToken, selectedCombos, Map.of(), voucherId);
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummaryWithWalletVoucher(Integer accountId, BookingSelection selection,
                                                            String holdToken,
                                                            Map<Long, Integer> selectedCombos,
                                                            Map<Long, Integer> selectedFoodItems,
                                                            Long voucherId) {
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos, selectedFoodItems);
        WalletVoucherOption voucherOption = voucherId == null
                ? null
                : evaluateWalletVoucher(accountId, voucherId, base, true);
        BigDecimal discount = voucherOption == null ? BigDecimal.ZERO : voucherOption.discount();
        String voucherCode = voucherOption == null ? null : voucherOption.voucher().getCode();
        return base.toSummary(discount, voucherCode);
    }

    @Transactional(readOnly = true)
    public List<WalletVoucherOption> getWalletVoucherOptions(Integer accountId, BookingSelection selection,
                                                             String holdToken,
                                                             Map<Long, Integer> selectedCombos) {
        return getWalletVoucherOptions(accountId, selection, holdToken, selectedCombos, Map.of());
    }

    @Transactional(readOnly = true)
    public List<WalletVoucherOption> getWalletVoucherOptions(Integer accountId, BookingSelection selection,
                                                             String holdToken,
                                                             Map<Long, Integer> selectedCombos,
                                                             Map<Long, Integer> selectedFoodItems) {
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos, selectedFoodItems);
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findWalletVouchers(requireAccountId(accountId)).stream()
                .filter(voucher -> !Boolean.TRUE.equals(voucher.getIsDeleted()))
                .filter(voucher -> voucher.getEndDate() != null && voucher.getEndDate().isAfter(now))
                .map(voucher -> evaluateWalletVoucher(accountId, voucher, base, false))
                .toList();
    }

    @Transactional
    public Booking createPendingBooking(Integer accountId, BookingSelection selection, String holdToken,
                                        Map<Long, Integer> selectedCombos, String voucherCode) {
        BookingSummary summary = calculateSummary(selection, holdToken, selectedCombos, voucherCode);
        return savePendingBooking(accountId, selection, summary);
    }

    @Transactional
    public Booking createPendingBookingWithWalletVoucher(Integer accountId, BookingSelection selection, String holdToken,
                                                         Map<Long, Integer> selectedCombos, Long voucherId) {
        return createPendingBookingWithWalletVoucher(accountId, selection, holdToken, selectedCombos, Map.of(), voucherId);
    }

    @Transactional
    public Booking createPendingBookingWithWalletVoucher(Integer accountId, BookingSelection selection, String holdToken,
                                                         Map<Long, Integer> selectedCombos,
                                                         Map<Long, Integer> selectedFoodItems, Long voucherId) {
        BookingSummary summary = calculateSummaryWithWalletVoucher(
                accountId, selection, holdToken, selectedCombos, selectedFoodItems, voucherId);
        return savePendingBooking(accountId, selection, summary);
    }

    private Booking savePendingBooking(Integer accountId, BookingSelection selection, BookingSummary summary) {
        /*
         * Booking PENDING là snapshot giá tại thời điểm xác nhận. Header giữ các tổng tiền,
         * còn vé/combo/món lẻ được lưu thành dòng chi tiết. Nhờ vậy thay đổi giá catalog
         * sau đó không làm biến đổi hóa đơn hay số tiền cổng thanh toán đã nhận.
         */
        LocalDateTime paymentExpiry = LocalDateTime.now().plusMinutes(5);
        Booking booking = new Booking();
        booking.setAccountId(accountId);
        booking.setShowtimeId(selection.showtimeId());
        booking.setStatus(Booking.Status.PENDING);
        booking.setTicketSubtotal(summary.ticketSubtotal());
        booking.setComboSubtotal(summary.comboSubtotal());
        booking.setFoodSubtotal(summary.foodSubtotal());
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
        for (FoodItemLine line : summary.foodItems()) {
            BookingFoodItem item = new BookingFoodItem();
            item.setBookingId(booking.getId());
            item.setFoodItemId(line.id());
            item.setFoodItemName(line.name());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.subtotal());
            bookingFoodItemRepository.save(item);
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
                bookingComboRepository.findByBookingId(booking.getId()),
                bookingFoodItemRepository.findByBookingId(booking.getId()));
    }

    private SummaryBase buildSummaryBase(BookingSelection selection, String holdToken,
                                         Map<Long, Integer> selectedCombos,
                                         Map<Long, Integer> selectedFoodItems) {
        /*
         * Tính tổng luôn bắt đầu từ các ghế HOLDING còn hạn. Mọi giá combo/món lẻ
         * đều được truy vấn lại bằng ID thay vì nhận giá từ trình duyệt.
         */
        List<BookingTicket> tickets = requireValidHolds(selection, holdToken);
        BigDecimal ticketSubtotal = tickets.stream().map(BookingTicket::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ComboLine> comboLines = new ArrayList<>();
        BigDecimal comboSubtotal = BigDecimal.ZERO;
        List<FoodItemLine> foodItemLines = new ArrayList<>();
        BigDecimal foodSubtotal = BigDecimal.ZERO;

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

        if (selectedFoodItems != null && !selectedFoodItems.isEmpty()) {
            Map<Long, FoodItem> foodItemMap = new HashMap<>();
            foodItemRepository.findAllById(selectedFoodItems.keySet())
                    .forEach(item -> foodItemMap.put(item.getId(), item));
            for (Map.Entry<Long, Integer> entry : selectedFoodItems.entrySet()) {
                FoodItem foodItem = foodItemMap.get(entry.getKey());
                if (foodItem == null || !ACTIVE_FOOD_STATUSES.contains(foodItem.getStatus()) || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Món lẻ đã chọn không còn khả dụng.");
                }
                BigDecimal subtotal = foodItem.getUnitPrice().multiply(BigDecimal.valueOf(entry.getValue()));
                foodItemLines.add(new FoodItemLine(foodItem.getId(), foodItem.getName(), foodItem.getCategory(),
                        entry.getValue(), foodItem.getUnitPrice(), subtotal));
                foodSubtotal = foodSubtotal.add(subtotal);
            }
        }

        LocalDateTime expiresAt = tickets.stream()
                .map(BookingTicket::getHoldExpiresAt)
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        return new SummaryBase(selection, tickets, comboLines, foodItemLines, ticketSubtotal, comboSubtotal,
                foodSubtotal, ticketSubtotal.add(comboSubtotal).add(foodSubtotal), expiresAt);
    }

    private WalletVoucherOption evaluateWalletVoucher(Integer accountId, Long voucherId,
                                                      SummaryBase base, boolean strict) {
        Voucher voucher = voucherRepository.findWalletVoucher(requireAccountId(accountId), voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher này chưa được lưu trong ví của bạn."));
        return evaluateWalletVoucher(accountId, voucher, base, strict);
    }

    private WalletVoucherOption evaluateWalletVoucher(Integer accountId, Voucher voucher,
                                                      SummaryBase base, boolean strict) {
        // strict=true dùng lúc áp voucher; strict=false dùng để liệt kê cả voucher chưa đủ điều kiện.
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
        /*
         * Thứ tự kiểm tra ưu tiên lỗi trạng thái/thời gian/số lượng trước điều kiện đơn hàng.
         * Hàm trả chuỗi lý do để màn summary giải thích vì sao từng voucher bị vô hiệu hóa.
         */
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
        // Voucher WATER áp dụng cho cả combo và món lẻ; TICKET chỉ áp dụng tiền ghế phù hợp.
        Voucher.ServiceScope scope = voucher.getServiceScope();
        if (scope == Voucher.ServiceScope.WATER) {
            return base.comboSubtotal().add(base.foodSubtotal());
        }
        BigDecimal ticketAmount = eligibleTicketAmount(voucher, base);
        if (scope == Voucher.ServiceScope.TICKET) {
            return ticketAmount;
        }
        return ticketAmount.add(base.comboSubtotal()).add(base.foodSubtotal());
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
        // Không cho tạo summary/booking từ token hết hạn hoặc ghế thuộc suất chiếu khác.
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

    // Các record dưới đây là view-model bất biến truyền giữa service, controller và Thymeleaf.
    public record ComboLine(Long id, String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) { }
    public record FoodItemLine(Long id, String name, String category, int quantity,
                               BigDecimal unitPrice, BigDecimal subtotal) { }
    public record WalletVoucherOption(Voucher voucher, BigDecimal discount, BigDecimal eligibleAmount,
                                      boolean eligible, String reason) { }
    public record BookingSummary(BookingSelection selection, List<BookingTicket> tickets, List<ComboLine> combos,
                                 List<FoodItemLine> foodItems, BigDecimal ticketSubtotal,
                                 BigDecimal comboSubtotal, BigDecimal foodSubtotal, BigDecimal discount,
                                 BigDecimal total, String voucherCode, LocalDateTime expiresAt) { }
    public record BookingDetails(Booking booking, Showtime showtime,
                                 List<BookingTicket> tickets, List<BookingCombo> combos,
                                 List<BookingFoodItem> foodItems) { }
    private record SummaryBase(BookingSelection selection, List<BookingTicket> tickets, List<ComboLine> combos,
                               List<FoodItemLine> foodItems, BigDecimal ticketSubtotal,
                               BigDecimal comboSubtotal, BigDecimal foodSubtotal, BigDecimal beforeDiscount,
                               LocalDateTime expiresAt) {
        BookingSummary toSummary(BigDecimal discount, String voucherCode) {
            BigDecimal safeDiscount = discount == null ? BigDecimal.ZERO : discount;
            return new BookingSummary(selection, tickets, combos, foodItems, ticketSubtotal, comboSubtotal, foodSubtotal,
                    safeDiscount, beforeDiscount.subtract(safeDiscount), voucherCode, expiresAt);
        }
    }
    private record VoucherRule(BigDecimal discountPercent, BigDecimal maxDiscount) { }
}
