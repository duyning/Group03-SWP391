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
    // Combo ACTIVE hoặc NEW đều được phép xuất hiện ở màn khách hàng.
    private static final Set<String> ACTIVE_COMBO_STATUSES = Set.of("ACTIVE", "NEW");

    // Món lẻ dùng cùng quy ước trạng thái với combo.
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
        // Lưu repository/helper do Spring inject; service không tự khởi tạo dependency.
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
        // Query theo tập trạng thái đang bán và sắp xếp tên A-Z.
        return comboRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_COMBO_STATUSES));
    }

    public List<FoodItem> getActiveFoodItems() {
        // Chuyển Set sang List để khớp chữ ký derived query.
        return foodItemRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_FOOD_STATUSES));
    }

    public LinkedHashMap<Long, Integer> validateComboQuantities(Map<String, String> params) {
        // LinkedHashMap giữ thứ tự input để summary hiển thị ổn định.
        LinkedHashMap<Long, Integer> selected = new LinkedHashMap<>();

        // @RequestParam Map chứa cả field khác nên phải lọc theo prefix.
        for (Map.Entry<String, String> entry : params.entrySet()) {
            // Chỉ xử lý input tên combo_{id}.
            if (!entry.getKey().startsWith("combo_")) continue;
            try {
                // Bỏ 6 ký tự "combo_" rồi parse phần còn lại thành khóa chính.
                long comboId = Long.parseLong(entry.getKey().substring(6));

                // Parse giá trị input number thành số lượng.
                int quantity = Integer.parseInt(entry.getValue());

                // Backend chặn lại dù frontend đã có min/max.
                if (quantity < 0 || quantity > 10) {
                    throw new IllegalArgumentException("Số lượng combo phải từ 0 đến 10.");
                }

                // Số lượng 0 nghĩa là không chọn, không cần lưu vào map.
                if (quantity > 0) selected.put(comboId, quantity);
            } catch (NumberFormatException ex) {
                // ID hoặc quantity sửa tay không phải số được đổi thành lỗi nghiệp vụ.
                throw new IllegalArgumentException("Số lượng combo không hợp lệ.");
            }
        }

        // Đọc lại toàn bộ Combo thật bằng các ID đã parse.
        List<Combo> combos = comboRepository.findAllById(selected.keySet());

        // Phải tìm đủ ID và mọi combo vẫn đang bán.
        if (combos.size() != selected.size()
                || combos.stream().anyMatch(combo -> !ACTIVE_COMBO_STATUSES.contains(combo.getStatus()))) {
            throw new IllegalArgumentException("Một combo đã ngừng bán. Vui lòng chọn lại.");
        }

        // Chỉ trả ID → quantity; không nhận giá từ request.
        return selected;
    }

    public LinkedHashMap<Long, Integer> validateFoodItemQuantities(Map<String, String> params) {
        // Map kết quả giữ thứ tự lựa chọn món lẻ.
        LinkedHashMap<Long, Integer> selected = new LinkedHashMap<>();

        // Duyệt toàn bộ form parameter.
        for (Map.Entry<String, String> entry : params.entrySet()) {
            // Chỉ xử lý input food_{id}.
            if (!entry.getKey().startsWith("food_")) continue;
            try {
                // Bỏ prefix 5 ký tự để lấy khóa chính.
                long foodItemId = Long.parseLong(entry.getKey().substring(5));

                // Parse số lượng do browser gửi.
                int quantity = Integer.parseInt(entry.getValue());

                // Chặn miền 0..10 ở server.
                if (quantity < 0 || quantity > 10) {
                    throw new IllegalArgumentException("Số lượng món lẻ phải từ 0 đến 10.");
                }

                // Không lưu lựa chọn bằng 0.
                if (quantity > 0) selected.put(foodItemId, quantity);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Số lượng món lẻ không hợp lệ.");
            }
        }

        // Đọc entity thật để kiểm tra tồn tại/trạng thái.
        List<FoodItem> foodItems = foodItemRepository.findAllById(selected.keySet());

        // Từ chối request chứa ID giả hoặc món đã ngừng bán.
        if (foodItems.size() != selected.size()
                || foodItems.stream().anyMatch(item -> !ACTIVE_FOOD_STATUSES.contains(item.getStatus()))) {
            throw new IllegalArgumentException("Một món lẻ đã ngừng bán. Vui lòng chọn lại.");
        }

        // Trả map đã xác minh.
        return selected;
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummary(BookingSelection selection, String holdToken,
                                           Map<Long, Integer> selectedCombos, String voucherCode) {
        // Overload cũ không có món lẻ nên truyền Map rỗng sang method đầy đủ.
        return calculateSummary(selection, holdToken, selectedCombos, Map.of(), voucherCode);
    }

    @Transactional(readOnly = true)
    public BookingSummary calculateSummary(BookingSelection selection, String holdToken,
                                           Map<Long, Integer> selectedCombos,
                                           Map<Long, Integer> selectedFoodItems, String voucherCode) {
        // Tính tiền vé/combo/món từ dữ liệu DB và xác minh hold còn hạn.
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos, selectedFoodItems);

        // Chuẩn hóa code voucher nhập trực tiếp: trim + uppercase, rỗng thành null.
        String normalizedVoucher = normalizeVoucher(voucherCode);

        // Không có code thì không query rule; có code phải tồn tại và active.
        VoucherRule voucherRule = normalizedVoucher == null ? null : voucherRule(normalizedVoucher);

        // Không voucher giảm 0; có voucher tính phần trăm, làm tròn xuống và chặn maxDiscount.
        BigDecimal discount = voucherRule == null ? BigDecimal.ZERO
                : base.beforeDiscount().multiply(voucherRule.discountPercent().divide(new BigDecimal("100"), 4, RoundingMode.DOWN))
                .setScale(0, RoundingMode.DOWN)
                .min(voucherRule.maxDiscount());

        // Tạo BookingSummary cuối cùng với total = beforeDiscount - discount.
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
        // Xác minh hold và tính các subtotal trước khi đánh giá voucher.
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos, selectedFoodItems);

        // null nghĩa là không dùng voucher; ID có giá trị phải được evaluate ở strict mode.
        WalletVoucherOption voucherOption = voucherId == null
                ? null
                : evaluateWalletVoucher(accountId, voucherId, base, true);

        // Không voucher có discount 0.
        BigDecimal discount = voucherOption == null ? BigDecimal.ZERO : voucherOption.discount();

        // Snapshot code voucher để booking không phụ thuộc ID về sau.
        String voucherCode = voucherOption == null ? null : voucherOption.voucher().getCode();

        // Kết hợp base + discount thành summary.
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
        // Dùng cùng một base để mọi voucher được so với cùng tổng tiền/ghế.
        SummaryBase base = buildSummaryBase(selection, holdToken, selectedCombos, selectedFoodItems);

        // Mốc hiện tại dùng để loại voucher đã hết hạn trước khi evaluate chi tiết.
        LocalDateTime now = LocalDateTime.now();

        // Chỉ đọc voucher nằm trong ví đúng account.
        return voucherRepository.findWalletVouchers(requireAccountId(accountId)).stream()
                // Loại voucher soft-deleted.
                .filter(voucher -> !Boolean.TRUE.equals(voucher.getIsDeleted()))
                // Loại voucher thiếu hạn hoặc đã hết hạn.
                .filter(voucher -> voucher.getEndDate() != null && voucher.getEndDate().isAfter(now))
                // strict=false giữ cả voucher chưa đủ điều kiện và trả reason cho UI.
                .map(voucher -> evaluateWalletVoucher(accountId, voucher, base, false))
                .toList();
    }

    @Transactional
    public Booking createPendingBooking(Integer accountId, BookingSelection selection, String holdToken,
                                        Map<Long, Integer> selectedCombos, String voucherCode) {
        // Tính/validate lần cuối ngay trong transaction tạo booking.
        BookingSummary summary = calculateSummary(selection, holdToken, selectedCombos, voucherCode);

        // Persist snapshot đơn và các dòng chi tiết.
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
        // Tính lại toàn bộ, không tái sử dụng summary do lần GET trước tạo.
        BookingSummary summary = calculateSummaryWithWalletVoucher(
                accountId, selection, holdToken, selectedCombos, selectedFoodItems, voucherId);

        // Chỉ lưu sau khi mọi validation hold/catalog/voucher đã thành công.
        return savePendingBooking(accountId, selection, summary);
    }

    private Booking savePendingBooking(Integer accountId, BookingSelection selection, BookingSummary summary) {
        // Booking và hold được gia hạn/chốt cùng hạn thanh toán 5 phút kể từ lúc xác nhận.
        LocalDateTime paymentExpiry = LocalDateTime.now().plusMinutes(5);

        // Tạo header booking mới chưa có ID.
        Booking booking = new Booking();

        // Gắn owner và suất chiếu lấy từ dữ liệu server/session đã xác thực.
        booking.setAccountId(accountId);
        booking.setShowtimeId(selection.showtimeId());

        // PENDING nghĩa là đã tạo đơn nhưng chưa có bằng chứng trả tiền.
        booking.setStatus(Booking.Status.PENDING);

        // Sao chép từng subtotal để hóa đơn giải thích được tổng tiền.
        booking.setTicketSubtotal(summary.ticketSubtotal());
        booking.setComboSubtotal(summary.comboSubtotal());
        booking.setFoodSubtotal(summary.foodSubtotal());
        booking.setDiscountAmount(summary.discount());

        // total là số tiền duy nhất PaymentService sẽ dùng.
        booking.setTotalAmount(summary.total());

        // Lưu code voucher dạng snapshot để không phụ thuộc quan hệ về sau.
        booking.setVoucherCode(summary.voucherCode());

        // Ghi thời điểm tạo và hạn thanh toán.
        booking.setCreatedAt(LocalDateTime.now());
        booking.setExpiresAt(paymentExpiry);

        // INSERT header trước để nhận booking.id dùng làm khóa ngoại logic cho các dòng sau.
        booking = bookingRepository.save(booking);

        // Chuyển các hold hiện tại thành hold đã gắn booking PENDING.
        for (BookingTicket ticket : summary.tickets()) {
            // Gắn ID booking vừa sinh.
            ticket.setBookingId(booking.getId());

            // Đồng bộ hạn ghế với hạn thanh toán của booking.
            ticket.setHoldExpiresAt(paymentExpiry);
        }

        // Batch UPDATE các BookingTicket.
        ticketRepository.saveAll(summary.tickets());

        // Sao chép từng combo thành snapshot booking_combos.
        for (ComboLine line : summary.combos()) {
            BookingCombo item = new BookingCombo();
            item.setBookingId(booking.getId());

            // Lưu cả catalog ID và tên/giá tại thời điểm mua.
            item.setComboId(line.id());
            item.setComboName(line.name());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.subtotal());

            // Persist dòng combo.
            bookingComboRepository.save(item);
        }

        // Sao chép món lẻ theo cùng nguyên tắc snapshot.
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

        // Trả entity đã có ID để controller redirect sang payment.
        return booking;
    }

    @Transactional(readOnly = true)
    public BookingDetails getBookingDetails(Long bookingId, Integer accountId) {
        // Query bằng cả ID và owner, ngăn tài khoản khác đoán bookingId để xem đơn.
        Booking booking = bookingRepository.findByIdAndAccountId(bookingId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));

        // Dùng helper chung để nạp các dòng chi tiết.
        return getBookingDetails(booking);
    }

    @Transactional(readOnly = true)
    public BookingDetails getBookingDetails(Long bookingId) {
        // Overload public dùng cho payment return/result nơi orderCode đã xác định booking.
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));
        return getBookingDetails(booking);
    }

    private BookingDetails getBookingDetails(Booking booking) {
        // Booking chỉ lưu showtimeId nên cần query Showtime để lấy phim/phòng/ngày giờ.
        Showtime showtime = showtimeRepository.findById(booking.getShowtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu của đơn vé."));

        // Gom header, showtime và ba loại dòng chi tiết vào record bất biến cho template.
        return new BookingDetails(booking, showtime, ticketRepository.findByBookingId(booking.getId()),
                bookingComboRepository.findByBookingId(booking.getId()),
                bookingFoodItemRepository.findByBookingId(booking.getId()));
    }

    private SummaryBase buildSummaryBase(BookingSelection selection, String holdToken,
                                         Map<Long, Integer> selectedCombos,
                                         Map<Long, Integer> selectedFoodItems) {
        // Chỉ các hold đúng token, đúng showtime, còn hạn và HOLDING được dùng tính đơn.
        List<BookingTicket> tickets = requireValidHolds(selection, holdToken);

        // Cộng giá snapshot do SeatHoldingService đã tính ở backend.
        BigDecimal ticketSubtotal = tickets.stream().map(BookingTicket::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // List và subtotal combo bắt đầu rỗng/0.
        List<ComboLine> comboLines = new ArrayList<>();
        BigDecimal comboSubtotal = BigDecimal.ZERO;

        // List và subtotal món lẻ bắt đầu rỗng/0.
        List<FoodItemLine> foodItemLines = new ArrayList<>();
        BigDecimal foodSubtotal = BigDecimal.ZERO;

        // Chỉ query catalog combo khi map thực sự có lựa chọn.
        if (selectedCombos != null && !selectedCombos.isEmpty()) {
            // Tạo map ID → Combo từ entity thật trong DB.
            Map<Long, Combo> comboMap = new HashMap<>();
            comboRepository.findAllById(selectedCombos.keySet()).forEach(combo -> comboMap.put(combo.getId(), combo));

            // Duyệt theo LinkedHashMap lựa chọn để giữ thứ tự hiển thị.
            for (Map.Entry<Long, Integer> entry : selectedCombos.entrySet()) {
                // Lookup entity theo ID.
                Combo combo = comboMap.get(entry.getKey());

                // Chặn ID giả, trạng thái ngừng bán hoặc quantity không dương.
                if (combo == null || !ACTIVE_COMBO_STATUSES.contains(combo.getStatus()) || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Combo đã chọn không còn khả dụng.");
                }

                // Thành tiền dòng = đơn giá DB × số lượng đã validate.
                BigDecimal subtotal = combo.getPrice().multiply(BigDecimal.valueOf(entry.getValue()));

                // Snapshot tên/giá/số lượng cho summary.
                comboLines.add(new ComboLine(combo.getId(), combo.getName(), entry.getValue(), combo.getPrice(), subtotal));

                // Cộng dồn subtotal.
                comboSubtotal = comboSubtotal.add(subtotal);
            }
        }

        // Món lẻ được xử lý độc lập với combo.
        if (selectedFoodItems != null && !selectedFoodItems.isEmpty()) {
            // Tạo map ID → FoodItem từ DB.
            Map<Long, FoodItem> foodItemMap = new HashMap<>();
            foodItemRepository.findAllById(selectedFoodItems.keySet())
                    .forEach(item -> foodItemMap.put(item.getId(), item));

            // Duyệt lựa chọn món lẻ.
            for (Map.Entry<Long, Integer> entry : selectedFoodItems.entrySet()) {
                // Lookup entity thật.
                FoodItem foodItem = foodItemMap.get(entry.getKey());

                // Chặn ID giả, trạng thái sai hoặc quantity không dương.
                if (foodItem == null || !ACTIVE_FOOD_STATUSES.contains(foodItem.getStatus()) || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Món lẻ đã chọn không còn khả dụng.");
                }

                // Thành tiền từ unitPrice DB.
                BigDecimal subtotal = foodItem.getUnitPrice().multiply(BigDecimal.valueOf(entry.getValue()));

                // Tạo snapshot dòng món lẻ.
                foodItemLines.add(new FoodItemLine(foodItem.getId(), foodItem.getName(), foodItem.getCategory(),
                        entry.getValue(), foodItem.getUnitPrice(), subtotal));

                // Cộng dồn tiền món lẻ.
                foodSubtotal = foodSubtotal.add(subtotal);
            }
        }

        // Nếu nhiều ticket có hạn lệch nhau, đơn dùng hạn sớm nhất để an toàn.
        LocalDateTime expiresAt = tickets.stream()
                .map(BookingTicket::getHoldExpiresAt)
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        // beforeDiscount = vé + combo + món lẻ.
        return new SummaryBase(selection, tickets, comboLines, foodItemLines, ticketSubtotal, comboSubtotal,
                foodSubtotal, ticketSubtotal.add(comboSubtotal).add(foodSubtotal), expiresAt);
    }

    private WalletVoucherOption evaluateWalletVoucher(Integer accountId, Long voucherId,
                                                      SummaryBase base, boolean strict) {
        // Native query yêu cầu voucher nằm trong ví đúng account.
        Voucher voucher = voucherRepository.findWalletVoucher(requireAccountId(accountId), voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher này chưa được lưu trong ví của bạn."));

        // Chuyển sang overload đã có entity để đánh giá điều kiện.
        return evaluateWalletVoucher(accountId, voucher, base, strict);
    }

    private WalletVoucherOption evaluateWalletVoucher(Integer accountId, Voucher voucher,
                                                      SummaryBase base, boolean strict) {
        // null reason nghĩa là đã vượt qua toàn bộ điều kiện.
        String reason = voucherIneligibilityReason(accountId, voucher, base);

        // Tính phần tiền của đơn nằm trong phạm vi dịch vụ/loại ghế voucher.
        BigDecimal eligibleAmount = eligibleVoucherAmount(voucher, base);

        // Chỉ tính discount khi không có lý do loại; ngược lại giữ 0.
        BigDecimal discount = reason == null ? calculateVoucherDiscount(voucher, eligibleAmount) : BigDecimal.ZERO;

        // Voucher hợp lệ hình thức nhưng không giảm được đồng nào cũng bị coi là không đủ điều kiện.
        if (reason == null && discount.compareTo(BigDecimal.ZERO) <= 0) {
            reason = "Voucher chưa tạo ra giá trị giảm cho đơn hàng hiện tại.";
        }

        // strict=true dùng lúc user áp dụng: lỗi phải dừng request.
        if (strict && reason != null) {
            throw new IllegalArgumentException(reason);
        }

        // strict=false dùng lúc liệt kê: trả cả voucher khóa cùng reason cho UI.
        return new WalletVoucherOption(voucher, discount, eligibleAmount, reason == null, reason);
    }

    private String voucherIneligibilityReason(Integer accountId, Voucher voucher, SummaryBase base) {
        // Bắt buộc có account trước khi kiểm tra giới hạn người dùng.
        requireAccountId(accountId);

        // Chụp thời gian một lần cho start/end.
        LocalDateTime now = LocalDateTime.now();

        // Soft-deleted voucher không được áp dụng.
        if (Boolean.TRUE.equals(voucher.getIsDeleted())) {
            return "Voucher này đã ngừng hoạt động.";
        }

        // Thiếu một trong hai mốc thời gian là cấu hình không hợp lệ.
        if (voucher.getStartDate() == null || voucher.getEndDate() == null) {
            return "Voucher thiếu thời gian áp dụng.";
        }

        // startDate trong tương lai.
        if (voucher.getStartDate().isAfter(now)) {
            return "Voucher chưa đến thời gian áp dụng.";
        }

        // endDate phải lớn hơn now; bằng now cũng được xem là hết hạn.
        if (!voucher.getEndDate().isAfter(now)) {
            return "Voucher đã hết hạn.";
        }

        // Chặn voucher đã dùng đủ tổng số lượng.
        if (voucher.getTotalQuantity() != null && voucher.getUsedQuantity() != null
                && voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            return "Voucher đã hết số lượng phát hành.";
        }

        // null minOrderValue được chuẩn hóa về 0.
        BigDecimal minOrderValue = safeMoney(voucher.getMinOrderValue());

        // So sánh trên tổng trước giảm.
        if (base.beforeDiscount().compareTo(minOrderValue) < 0) {
            return "Đơn hàng chưa đạt tối thiểu "
                    + minOrderValue.setScale(0, RoundingMode.DOWN).toPlainString() + "đ.";
        }

        // Kiểm tra WEEKDAY/WEEKEND/ALL theo ngày chiếu, không theo ngày đặt.
        if (!matchesApplicableDay(voucher.getApplicableDays(), base.selection().showDate())) {
            return "Voucher không áp dụng cho ngày chiếu đã chọn.";
        }

        // Voucher cấm ngày lễ được so với danh sách ngày lễ hệ thống biết.
        if (Boolean.FALSE.equals(voucher.getIsHolidayApplicable()) && isKnownHoliday(base.selection().showDate())) {
            return "Voucher không áp dụng vào ngày lễ.";
        }

        // Phạm vi voucher phải có ít nhất một đồng eligible.
        if (eligibleVoucherAmount(voucher, base).compareTo(BigDecimal.ZERO) <= 0) {
            return "Đơn hàng hiện tại không có dịch vụ phù hợp với voucher.";
        }

        // limitPerUser thiếu mặc định mỗi user một lượt.
        int limitPerUser = voucher.getLimitPerUser() == null ? 1 : voucher.getLimitPerUser();

        // Chỉ query usage khi giới hạn dương và voucher có code.
        if (limitPerUser > 0 && voucher.getCode() != null) {
            // PENDING và PAID đều chiếm lượt để tránh tạo nhiều đơn song song.
            long usedByAccount = bookingRepository.countByAccountIdAndVoucherCodeAndStatusIn(
                    accountId,
                    voucher.getCode(),
                    List.of(Booking.Status.PENDING, Booking.Status.PAID)
            );

            // Đã đạt giới hạn thì khóa voucher.
            if (usedByAccount >= limitPerUser) {
                return "Bạn đã dùng đủ số lượt cho voucher này.";
            }
        }

        // Discount phải có type và value dương.
        if (voucher.getDiscountType() == null || voucher.getDiscountValue() == null
                || voucher.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            return "Voucher chưa có cấu hình giảm giá hợp lệ.";
        }

        // null là tín hiệu voucher đủ điều kiện.
        return null;
    }

    private BigDecimal calculateVoucherDiscount(Voucher voucher, BigDecimal eligibleAmount) {
        // Biến kết quả được tính khác nhau theo PERCENTAGE hoặc FIXED.
        BigDecimal discount;

        // Voucher phần trăm.
        if (voucher.getDiscountType() == Voucher.DiscountType.PERCENTAGE) {
            // Chia phần trăm với scale 4, nhân eligible và làm tròn xuống đơn vị đồng.
            discount = eligibleAmount
                    .multiply(voucher.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.DOWN))
                    .setScale(0, RoundingMode.DOWN);

            // maxDiscount chỉ áp dụng khi được cấu hình dương.
            BigDecimal maxDiscount = voucher.getMaxDiscountAmount();
            if (maxDiscount != null && maxDiscount.compareTo(BigDecimal.ZERO) > 0) {
                discount = discount.min(maxDiscount);
            }
        } else {
            // Voucher cố định lấy trực tiếp discountValue và làm tròn xuống.
            discount = voucher.getDiscountValue().setScale(0, RoundingMode.DOWN);
        }

        // Không cho giảm âm hoặc lớn hơn phần tiền đủ điều kiện.
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
        // Không có token nghĩa là chưa giữ ghế hoặc session đã bị dọn.
        if (holdToken == null || holdToken.isBlank()) {
            throw new IllegalArgumentException("Phiên giữ ghế không tồn tại.");
        }

        // Query theo token rồi giữ đúng showtime của selection để chống token dùng chéo suất.
        List<BookingTicket> tickets = ticketRepository.findByHoldToken(holdToken).stream()
                .filter(ticket -> selection.showtimeId().equals(ticket.getShowtimeId()))
                .toList();

        // Không tìm thấy ticket nghĩa là hold đã bị xóa/hết hạn.
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa chọn ghế hoặc thời gian giữ ghế đã hết.");
        }

        // Mọi ticket phải còn HOLDING, có expiresAt và chưa quá hạn.
        if (tickets.stream().anyMatch(ticket -> ticket.getStatus() != BookingTicket.Status.HOLDING
                || ticket.getHoldExpiresAt() == null || ticket.getHoldExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new IllegalArgumentException("Thời gian giữ ghế đã hết. Vui lòng chọn lại ghế.");
        }

        // Trả danh sách hợp lệ cho phép tính tiền/tạo booking.
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
