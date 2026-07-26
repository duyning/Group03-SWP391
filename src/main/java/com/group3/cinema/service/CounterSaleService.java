/**
 * Service nghiệp vụ bán vé tại quầy POS.
 *
 * Đây là service trung tâm của flow `/admin/counter-sales`.
 *
 * Luồng tổng quan theo màn hình:
 * 1. Bước chọn suất/ghế (`counter-sales.html`):
 *    - Frontend gọi `getSellableShowtimes(...)` để lấy suất còn bán.
 *    - Nhân viên chọn một suất, frontend gọi `getSeatMap(...)`.
 *    - `getSeatMap(...)` gọi `SeatHoldingService` để ghép sơ đồ ghế vật lý với trạng thái BOOKED/HOLDING.
 *    - Khi nhân viên chọn ghế, frontend gọi `holdSeats(...)`.
 *    - `holdSeats(...)` ghi bản ghi `booking_tickets` status HOLDING trong 5 phút.
 * 2. Bước hóa đơn/thanh toán (`counter-sales-checkout.html`):
 *    - Frontend gọi `getActiveCombos(...)`, `getActiveVouchers(...)`, `searchCustomers(...)`.
 *    - Mỗi lần thay đổi combo/voucher/loại khách, frontend gọi `previewSale(...)`.
 *    - `previewSale(...)` dựng hóa đơn nháp từ ghế đang HOLDING, combo và voucher.
 * 3. Chốt tiền mặt:
 *    - Frontend gọi `completeSale(...)` với paymentMethod CASH.
 *    - Service tạo `Booking` PAID, đổi ghế HOLDING -> BOOKED, tạo `Payment` SUCCESS,
 *      tạo các bản ghi `Ticket` để in/hiển thị, tăng used quantity voucher và cộng điểm thành viên.
 * 4. Chốt payOS:
 *    - Frontend gọi `createCounterPayment(...)` với paymentMethod PAYOS.
 *    - Service tạo `Booking` PENDING, giữ ghế vẫn HOLDING, tạo `Payment` PENDING,
 *      rồi gọi `PaymentGatewayRouter` sinh checkout URL payOS.
 *    - Khi payOS callback thành công, flow thanh toán chung của project sẽ cập nhật booking/payment.
 *
 * Các bảng chính bị tác động:
 * - `showtimes`, `rooms`, `seats`: đọc để xác định suất/phòng/sơ đồ.
 * - `booking_tickets`: giữ ghế và chống trùng ghế.
 * - `customer_bookings`: hóa đơn tổng.
 * - `booking_combos`: combo bán kèm.
 * - `payments`: giao dịch CASH/PAYOS.
 * - `ticket`: vé hiển thị/in sau khi thanh toán thành công.
 */
package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSeatView;
import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Combo;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.entity.Role;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.HolidayRepository;
import com.group3.cinema.repository.PaymentRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import com.group3.cinema.service.payment.PaymentGatewayRouter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Service
public class CounterSaleService {
    private static final Long DEFAULT_CINEMA_ID = 1L;
    private static final String WALK_IN_EMAIL = "walkin@counter.local";
    private static final Set<String> ACTIVE_COMBO_STATUSES = Set.of("ACTIVE", "NEW");

    private final ShowtimeRepository showtimeRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final BookingTicketRepository bookingTicketRepository;
    private final BookingRepository bookingRepository;
    private final BookingComboRepository bookingComboRepository;
    private final ComboRepository comboRepository;
    private final VoucherRepository voucherRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final AccountRepository accountRepository;
    private final HolidayRepository holidayRepository;
    private final SeatHoldingService seatHoldingService;
    private final TicketService ticketService;
    private final PaymentGatewayRouter paymentGatewayRouter;
    private final LoyaltyService loyaltyService;

    public CounterSaleService(ShowtimeRepository showtimeRepository,
                              RoomRepository roomRepository,
                              SeatRepository seatRepository,
                              BookingTicketRepository bookingTicketRepository,
                              BookingRepository bookingRepository,
                              BookingComboRepository bookingComboRepository,
                              ComboRepository comboRepository,
                              VoucherRepository voucherRepository,
                              PaymentRepository paymentRepository,
                              TicketRepository ticketRepository,
                              AccountRepository accountRepository,
                              HolidayRepository holidayRepository,
                              SeatHoldingService seatHoldingService,
                              TicketService ticketService,
                              PaymentGatewayRouter paymentGatewayRouter,
                              LoyaltyService loyaltyService) {
        this.showtimeRepository = showtimeRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.bookingTicketRepository = bookingTicketRepository;
        this.bookingRepository = bookingRepository;
        this.bookingComboRepository = bookingComboRepository;
        this.comboRepository = comboRepository;
        this.voucherRepository = voucherRepository;
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
        this.accountRepository = accountRepository;
        this.holidayRepository = holidayRepository;
        this.seatHoldingService = seatHoldingService;
        this.ticketService = ticketService;
        this.paymentGatewayRouter = paymentGatewayRouter;
        this.loyaltyService = loyaltyService;
    }

    /**
     * Lấy danh sách suất chiếu còn bán tại quầy.
     *
     * Luồng gọi:
     * - `CounterSaleApiController.showtimes(...)` nhận date/movieId từ frontend.
     * - Service gọi `ShowtimeRepository.searchShowtimes(...)`.
     * - Chỉ giữ lại suất chưa bắt đầu:
     *   + Ngày chiếu sau hôm nay.
     *   + Hoặc ngày chiếu là hôm nay nhưng giờ chiếu lớn hơn giờ hiện tại.
     * - Mỗi suất được chuyển thành `ShowtimeOption`, gồm sức chứa, số ghế đã bán và số ghế còn lại.
     */
    @Transactional(readOnly = true)
    public List<ShowtimeOption> getSellableShowtimes(LocalDate date, Integer movieId) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        return showtimeRepository.searchShowtimes(movieId, null, targetDate, targetDate).stream()
                .filter(showtime -> showtime.getShowDate().isAfter(today)
                        || (showtime.getShowDate().isEqual(today) && showtime.getShowTime().isAfter(now)))
                .map(this::toShowtimeOption)
                .toList();
    }

    /**
     * Tìm khách hàng thành viên tại quầy.
     *
     * Luồng gọi:
     * - Checkout page gọi khi nhân viên gõ tên/email/số điện thoại.
     * - Repository chỉ tìm account role CUSTOMER.
     * - Trả tối đa 12 gợi ý để dropdown nhẹ và dễ chọn.
     */
    @Transactional(readOnly = true)
    public List<CustomerOption> searchCustomers(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        return accountRepository.searchCustomers(Role.CUSTOMER, normalized).stream()
                .limit(12)
                .map(account -> new CustomerOption(
                        account.getAccountID(),
                        account.getName(),
                        account.getEmail(),
                        account.getPhoneNum(),
                        account.getMembershipLevel() != null ? account.getMembershipLevel().name() : "SILVER"
                ))
                .toList();
    }

    /**
     * Lấy combo đang mở bán để bán kèm hóa đơn tại quầy.
     *
     * Chỉ lấy status ACTIVE hoặc NEW, không lấy món lẻ/đồ đã ngừng bán.
     */
    @Transactional(readOnly = true)
    public List<ComboOption> getActiveCombos() {
        return comboRepository.findByStatusInOrderByNameAsc(List.copyOf(ACTIVE_COMBO_STATUSES)).stream()
                .map(combo -> new ComboOption(
                        combo.getId(),
                        combo.getName(),
                        combo.getDescription(),
                        combo.getImage(),
                        safeMoney(combo.getPrice()),
                        combo.getStatus()
                ))
                .toList();
    }

    /**
     * Lấy voucher có thể dùng tại quầy.
     *
     * Điều kiện lọc ban đầu:
     * - Voucher đang trong thời gian active theo repository.
     * - Chưa bị xóa mềm.
     * - Chưa dùng hết tổng số lượng phát hành.
     *
     * Các điều kiện sâu hơn như ngày áp dụng, ngày lễ, min order, loại dịch vụ/loại ghế
     * được kiểm tra ở `evaluateVoucher(...)` khi có hóa đơn cụ thể.
     */
    @Transactional(readOnly = true)
    public List<VoucherOption> getActiveVouchers() {
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findActiveVouchers(now).stream()
                .filter(voucher -> !Boolean.TRUE.equals(voucher.getIsDeleted()))
                .filter(voucher -> voucher.getTotalQuantity() == null || voucher.getUsedQuantity() == null
                        || voucher.getUsedQuantity() < voucher.getTotalQuantity())
                .map(voucher -> new VoucherOption(
                        voucher.getId(),
                        voucher.getCode(),
                        voucher.getTitle(),
                        voucher.getDiscountType() != null ? voucher.getDiscountType().getDisplayName() : "",
                        safeMoney(voucher.getDiscountValue()),
                        safeMoney(voucher.getMinOrderValue()),
                        voucher.getEndDate()
                ))
                .toList();
    }

    /**
     * Tải sơ đồ ghế cho một suất chiếu tại quầy.
     *
     * Luồng gọi:
     * - Frontend gửi `showtimeId` sau khi nhân viên chọn suất.
     * - `selectionFor(showtimeId)` map suất chiếu sang `BookingSelection` gồm movie, room, ngày giờ.
     * - Gọi `SeatHoldingService.getSeatMap(selection, holdToken)`.
     * - Service con đọc `seats` của phòng và `booking_tickets` của suất để xác định từng ô:
     *   AVAILABLE, SELECTED, HOLDING, BOOKED hoặc UNAVAILABLE.
     * - Trả rows/cols để giao diện vẽ sơ đồ full theo đúng cấu hình phòng.
     */
    @Transactional
    public SeatMapResponse getSeatMap(Long showtimeId, String holdToken) {
        BookingSelection selection = selectionFor(showtimeId);
        List<BookingSeatView> seats = seatHoldingService.getSeatMap(selection, holdToken);
        int rows = seats.stream().mapToInt(BookingSeatView::rowIndex).max().orElse(-1) + 1;
        int cols = seats.stream()
                .mapToInt(seat -> seat.colIndex() + Math.max(1, seat.capacity()))
                .max()
                .orElse(0);
        return new SeatMapResponse(selection, seats, rows, cols);
    }

    /**
     * Giữ ghế tại quầy trong 5 phút.
     *
     * Luồng gọi:
     * - Frontend gọi sau mỗi lần nhân viên xác nhận danh sách ghế ở bước 1.
     * - Service kiểm tra suất còn bán bằng `ensureShowtimeStillSellable(...)`.
     * - Gọi `SeatHoldingService.holdSeats(...)` để:
     *   + Validate ghế thuộc đúng phòng và là ghế bán được.
     *   + Chặn quá 8 chỗ, ghế đôi tính theo capacity.
     *   + Chặn ghế đã BOOKED hoặc đang HOLDING bởi token khác.
     *   + Ghi `booking_tickets` status HOLDING kèm holdToken/holdExpiresAt.
     * - Trả lại token và danh sách vé tạm để bước checkout tính tiền.
     */
    @Transactional
    public HoldResponse holdSeats(HoldRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu giữ ghế không hợp lệ.");
        }
        BookingSelection selection = selectionFor(request.showtimeId());
        ensureShowtimeStillSellable(selection);
        SeatHoldingService.HoldResult hold = seatHoldingService.holdSeats(
                selection,
                request.seatIds(),
                request.holdToken()
        );
        return new HoldResponse(hold.token(), hold.expiresAt(), hold.tickets().stream()
                .map(ticket -> new TicketLine(ticket.getSeatId(), ticket.getSeatLabel(), ticket.getSeatType(), ticket.getPrice()))
                .toList());
    }

    /**
     * Giải phóng ghế đang giữ theo token.
     *
     * Dùng khi nhân viên bấm đổi ghế, quay lại bước chọn ghế hoặc đóng màn checkout.
     * Chỉ xóa ghế HOLDING chưa gắn booking; ghế đã BOOKED không bị ảnh hưởng.
     */
    @Transactional
    public void releaseHold(String holdToken) {
        seatHoldingService.releaseHold(holdToken);
    }

    /**
     * Xem trước hóa đơn tại quầy.
     *
     * `strict = false` nghĩa là voucher không hợp lệ sẽ không làm hỏng toàn bộ preview.
     * Service vẫn trả tổng tiền và `voucherMessage` để frontend báo lý do cho nhân viên.
     */
    @Transactional(readOnly = true)
    public CounterSaleSummary previewSale(CounterSaleRequest request) {
        return buildDraft(request, false).toSummary();
    }

    /**
     * Hoàn tất đơn bán vé trực tiếp bằng tiền mặt.
     *
     * Luồng xử lý chi tiết:
     * 1. `buildDraft(request, true)` dựng hóa đơn ở chế độ strict:
     *    - Ghế phải còn HOLDING và chưa hết hạn.
     *    - Combo phải còn bán.
     *    - Voucher nếu nhập phải thỏa toàn bộ điều kiện.
     * 2. Chỉ cho phép CASH ở endpoint này.
     *    - PAYOS phải đi qua `createCounterPayment(...)` để sinh link thanh toán.
     * 3. `resolveCustomerAccount(...)`:
     *    - Nếu chọn khách thành viên thì dùng account đó.
     *    - Nếu chỉ nhập phone/email trùng khách cũ thì dùng khách cũ.
     *    - Nếu không có khách, dùng account khách vãng lai tại quầy.
     * 4. Tạo `Booking` status PAID trong bảng `customer_bookings`.
     * 5. Lấy các dòng `booking_tickets` theo holdToken:
     *    - Gắn `bookingId`.
     *    - Đổi status HOLDING -> BOOKED.
     *    - Xóa holdToken/holdExpiresAt vì ghế đã bán thành công.
     * 6. Lưu combo đã chọn vào `booking_combos`.
     * 7. Tăng `vouchers.used_quantity` nếu có áp voucher.
     * 8. Tạo `Payment` SUCCESS, method CASH, orderCode dạng POS...
     * 9. Tạo các bản ghi `Ticket` dùng cho trang vé/QR/in vé.
     * 10. Nếu là khách thành viên thật, cộng điểm loyalty.
     */
    @Transactional
    public CounterSaleResult completeSale(CounterSaleRequest request) {
        SaleDraft draft = buildDraft(request, true);
        Payment.Method paymentMethod = resolvePaymentMethod(request.paymentMethod());
        if (paymentMethod == Payment.Method.PAYOS) {
            throw new IllegalArgumentException("Thanh toán QR/payOS cần tạo mã thanh toán trước, không được chốt trực tiếp.");
        }
        Account customer = resolveCustomerAccount(request);
        LocalDateTime now = LocalDateTime.now();

        Booking booking = new Booking();
        booking.setAccountId(customer.getAccountID());
        booking.setShowtimeId(draft.selection().showtimeId());
        booking.setStatus(Booking.Status.PAID);
        booking.setTicketSubtotal(draft.ticketSubtotal());
        booking.setComboSubtotal(draft.comboSubtotal());
        booking.setDiscountAmount(draft.discount());
        booking.setTotalAmount(draft.total());
        booking.setVoucherCode(draft.voucherCode());
        booking.setCreatedAt(now);
        booking.setPaidAt(now);
        booking.setExpiresAt(now.plusMinutes(1));
        booking = bookingRepository.save(booking);

        Map<Long, BigDecimal> pricesBySeat = new HashMap<>();
        draft.tickets().forEach(ticket -> pricesBySeat.put(ticket.seatId(), ticket.price()));
        List<BookingTicket> heldTickets = bookingTicketRepository.findByHoldToken(request.holdToken()).stream()
                .filter(ticket -> draft.selection().showtimeId().equals(ticket.getShowtimeId()))
                .toList();
        for (BookingTicket ticket : heldTickets) {
            ticket.setBookingId(booking.getId());
            ticket.setStatus(BookingTicket.Status.BOOKED);
            ticket.setHoldToken(null);
            ticket.setHoldExpiresAt(null);
            ticket.setPrice(pricesBySeat.getOrDefault(ticket.getSeatId(), ticket.getPrice()));
        }
        bookingTicketRepository.saveAll(heldTickets);

        for (ComboLine line : draft.combos()) {
            BookingCombo item = new BookingCombo();
            item.setBookingId(booking.getId());
            item.setComboId(line.id());
            item.setComboName(line.name());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.subtotal());
            bookingComboRepository.save(item);
        }

        markVoucherAsUsed(draft.voucherCode());

        Payment payment = new Payment();
        payment.setBookingId(booking.getId());
        payment.setPaymentMethod(paymentMethod);
        payment.setOrderCode(resolveCounterOrderCode(paymentMethod, request.counterPaymentCode()));
        payment.setAmount(draft.total());
        payment.setStatus(Payment.Status.SUCCESS);
        payment.setResponseCode("00");
        payment.setTransactionId("COUNTER-" + UUID.randomUUID());
        payment.setCreatedAt(now);
        payment.setPaidAt(now);
        payment = paymentRepository.save(payment);

        createDisplayTickets(customer, draft, heldTickets, booking, payment, request);

        if (customer != null && !WALK_IN_EMAIL.equals(customer.getEmail())) {
            loyaltyService.addLoyaltyPoints(customer.getAccountID(), booking.getTotalAmount());
        }

        return new CounterSaleResult(
                booking.getId(),
                payment.getOrderCode(),
                payment.getPaymentMethod().name(),
                draft.total(),
                draft.tickets().stream().map(TicketLine::label).toList(),
                paymentMethod != Payment.Method.CASH,
                paymentQrContent(paymentMethod, payment.getOrderCode(), draft.total()),
                "Bán vé tại quầy thành công."
        );
    }

    /**
     * Tạo link thanh toán payOS cho đơn tại quầy.
     *
     * Luồng xử lý:
     * 1. Dựng hóa đơn strict giống chốt tiền mặt.
     * 2. Chỉ chấp nhận paymentMethod PAYOS; CASH không cần QR/link.
     * 3. Tạo `Booking` status PENDING:
     *    - Booking này đã có tổng tiền, voucher, combo.
     *    - Chưa `paidAt` vì khách chưa thanh toán trên payOS.
     *    - `expiresAt` bám theo thời hạn giữ ghế 5 phút.
     * 4. Gắn các ghế HOLDING vào booking nhưng vẫn giữ status HOLDING.
     *    - Mục đích: giữ ghế cho khách trong khi đang thanh toán QR.
     * 5. Lưu combo vào `booking_combos`.
     * 6. Tạo `Payment` status PENDING, method PAYOS, orderCode dạng số phù hợp payOS.
     * 7. Gọi `PaymentGatewayRouter.createRedirectUrl(...)`.
     * 8. Trả checkoutUrl để frontend chuyển thẳng sang trang payOS.
     *
     * Sau bước này, việc đổi `Payment` sang SUCCESS và `Booking` sang PAID thuộc flow callback/return payment chung.
     */
    @Transactional
    public CounterPaymentResult createCounterPayment(CounterSaleRequest request, HttpServletRequest httpRequest) {
        SaleDraft draft = buildDraft(request, true);
        Payment.Method paymentMethod = resolvePaymentMethod(request.paymentMethod());
        if (paymentMethod == Payment.Method.CASH) {
            throw new IllegalArgumentException("Tiền mặt không cần tạo mã QR thanh toán.");
        }
        if (paymentMethod != Payment.Method.PAYOS) {
            throw new IllegalArgumentException("Mã QR thanh toán tại quầy chỉ hỗ trợ cổng payOS/VietQR.");
        }

        Account customer = resolveCustomerAccount(request);
        LocalDateTime now = LocalDateTime.now();

        Booking booking = new Booking();
        booking.setAccountId(customer.getAccountID());
        booking.setShowtimeId(draft.selection().showtimeId());
        booking.setStatus(Booking.Status.PENDING);
        booking.setTicketSubtotal(draft.ticketSubtotal());
        booking.setComboSubtotal(draft.comboSubtotal());
        booking.setDiscountAmount(draft.discount());
        booking.setTotalAmount(draft.total());
        booking.setVoucherCode(draft.voucherCode());
        booking.setCreatedAt(now);
        booking.setExpiresAt(now.plusMinutes(SeatHoldingService.HOLD_MINUTES));
        booking = bookingRepository.save(booking);

        Map<Long, BigDecimal> pricesBySeat = new HashMap<>();
        draft.tickets().forEach(ticket -> pricesBySeat.put(ticket.seatId(), ticket.price()));
        List<BookingTicket> heldTickets = bookingTicketRepository.findByHoldToken(request.holdToken()).stream()
                .filter(ticket -> draft.selection().showtimeId().equals(ticket.getShowtimeId()))
                .toList();
        for (BookingTicket ticket : heldTickets) {
            ticket.setBookingId(booking.getId());
            ticket.setStatus(BookingTicket.Status.HOLDING);
            ticket.setPrice(pricesBySeat.getOrDefault(ticket.getSeatId(), ticket.getPrice()));
        }
        bookingTicketRepository.saveAll(heldTickets);

        for (ComboLine line : draft.combos()) {
            BookingCombo item = new BookingCombo();
            item.setBookingId(booking.getId());
            item.setComboId(line.id());
            item.setComboName(line.name());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.subtotal());
            bookingComboRepository.save(item);
        }

        Payment payment = new Payment();
        payment.setBookingId(booking.getId());
        payment.setPaymentMethod(paymentMethod);
        payment.setOrderCode(generatePayOsOrderCode());
        payment.setAmount(draft.total());
        payment.setStatus(Payment.Status.PENDING);
        payment.setCreatedAt(now);
        payment = paymentRepository.save(payment);

        String checkoutUrl = absoluteUrl(paymentGatewayRouter.createRedirectUrl(payment, booking, httpRequest), httpRequest);
        return new CounterPaymentResult(
                booking.getId(),
                payment.getOrderCode(),
                payment.getPaymentMethod().name(),
                draft.total(),
                checkoutUrl,
                checkoutUrl,
                "Đã tạo mã thanh toán payOS/VietQR."
        );
    }

    private String absoluteUrl(String url, HttpServletRequest request) {
        if (url == null || url.isBlank() || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        return base + url;
    }

    /**
     * Dựng hóa đơn nội bộ từ request hiện tại.
     *
     * Đây là hàm dùng chung cho preview, chốt tiền mặt và tạo payOS:
     * - `selectionFor(...)`: xác định suất/phòng/phim.
     * - `heldTicketLines(...)`: lấy các ghế đang HOLDING theo token và tính lại giá vé.
     * - `comboLines(...)`: gom số lượng combo, validate combo còn bán.
     * - `evaluateVoucher(...)`: tính chiết khấu theo voucher.
     * - Trả `SaleDraft` gồm ticketSubtotal, comboSubtotal, discount, total.
     *
     * `strict = false`: dùng cho preview, voucher không hợp lệ chỉ trả message.
     * `strict = true`: dùng khi tạo đơn thật, mọi lỗi nghiệp vụ đều throw để không lưu đơn sai.
     */
    private SaleDraft buildDraft(CounterSaleRequest request, boolean strict) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đơn bán vé không hợp lệ.");
        }
        BookingSelection selection = selectionFor(request.showtimeId());
        ensureShowtimeStillSellable(selection);
        Showtime showtime = showtimeRepository.findById(selection.showtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));
        List<TicketLine> tickets = heldTicketLines(selection, request.holdToken(), request.customerType(), showtime);
        BigDecimal ticketSubtotal = tickets.stream().map(TicketLine::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ComboLine> combos = comboLines(request.comboItems());
        BigDecimal comboSubtotal = combos.stream().map(ComboLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal beforeDiscount = ticketSubtotal.add(comboSubtotal);
        VoucherDiscount voucherDiscount = evaluateVoucher(request.voucherCode(), selection, tickets, ticketSubtotal, comboSubtotal, beforeDiscount, strict);
        BigDecimal total = beforeDiscount.subtract(voucherDiscount.discount()).max(BigDecimal.ZERO);
        return new SaleDraft(selection, tickets, combos, ticketSubtotal, comboSubtotal,
                voucherDiscount.discount(), total, voucherDiscount.code(), voucherDiscount.reason());
    }

    /**
     * Lấy danh sách ghế đang giữ và tính lại giá vé tại thời điểm thanh toán.
     *
     * Lý do không tin hoàn toàn giá gửi từ frontend:
     * - Người dùng có thể sửa request.
     * - Giá vé phụ thuộc suất chiếu, loại ghế, loại khách, ngày lễ...
     * - Vì vậy backend luôn đọc `booking_tickets`, `seats`, `showtime`
     *   rồi gọi `TicketService.calculatePrice(...)` để tính lại.
     */
    private List<TicketLine> heldTicketLines(BookingSelection selection, String holdToken,
                                             String customerType, Showtime showtime) {
        if (holdToken == null || holdToken.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn và giữ ghế trước khi thanh toán.");
        }
        List<BookingTicket> tickets = bookingTicketRepository.findByHoldToken(holdToken).stream()
                .filter(ticket -> selection.showtimeId().equals(ticket.getShowtimeId()))
                .toList();
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("Phiên giữ ghế không còn tồn tại. Vui lòng chọn lại ghế.");
        }
        if (tickets.stream().anyMatch(ticket -> ticket.getStatus() != BookingTicket.Status.HOLDING
                || ticket.getHoldExpiresAt() == null || ticket.getHoldExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new IllegalArgumentException("Ghế đang giữ đã hết hạn. Vui lòng chọn lại.");
        }

        Map<Long, Seat> seats = new HashMap<>();
        seatRepository.findAllById(tickets.stream().map(BookingTicket::getSeatId).toList())
                .forEach(seat -> seats.put(seat.getId(), seat));
        String type = normalizeCustomerType(customerType);
        return tickets.stream()
                .sorted(Comparator.comparing(BookingTicket::getSeatLabel))
                .map(ticket -> {
                    Seat seat = seats.get(ticket.getSeatId());
                    BigDecimal price = seat == null
                            ? ticket.getPrice()
                            : BigDecimal.valueOf(ticketService.calculatePrice(showtime, seat, type))
                            .setScale(0, RoundingMode.HALF_UP);
                    return new TicketLine(ticket.getSeatId(), ticket.getSeatLabel(), ticket.getSeatType(), price);
                })
                .toList();
    }

    /**
     * Chuẩn hóa danh sách combo chọn thêm.
     *
     * - Gộp các dòng trùng comboId.
     * - Bỏ qua dòng null/số lượng <= 0.
     * - Chặn số lượng mỗi loại quá 20 để tránh thao tác sai tại quầy.
     * - Đọc lại giá combo từ DB, không lấy giá từ frontend.
     */
    private List<ComboLine> comboLines(List<ComboSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (ComboSelection selection : selections) {
            if (selection == null || selection.comboId() == null || selection.quantity() == null || selection.quantity() <= 0) {
                continue;
            }
            if (selection.quantity() > 20) {
                throw new IllegalArgumentException("Số lượng combo mỗi loại không được vượt quá 20.");
            }
            quantities.merge(selection.comboId(), selection.quantity(), Integer::sum);
        }
        if (quantities.isEmpty()) {
            return List.of();
        }
        Map<Long, Combo> combos = new HashMap<>();
        comboRepository.findAllById(quantities.keySet()).forEach(combo -> combos.put(combo.getId(), combo));
        List<ComboLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Combo combo = combos.get(entry.getKey());
            if (combo == null || !ACTIVE_COMBO_STATUSES.contains(combo.getStatus())) {
                throw new IllegalArgumentException("Combo đã chọn không còn bán.");
            }
            BigDecimal unitPrice = safeMoney(combo.getPrice());
            lines.add(new ComboLine(combo.getId(), combo.getName(), entry.getValue(),
                    unitPrice, unitPrice.multiply(BigDecimal.valueOf(entry.getValue()))));
        }
        return lines;
    }

    /**
     * Kiểm tra và tính chiết khấu voucher cho hóa đơn tại quầy.
     *
     * Các điều kiện được xét theo đúng đơn hiện tại:
     * - Voucher còn thời gian, chưa xóa, chưa hết số lượng phát hành.
     * - Đơn đạt min order.
     * - Ngày chiếu phù hợp applicableDays và ngày lễ.
     * - Dịch vụ áp dụng đúng: vé, combo hoặc cả hai.
     * - Nếu voucher chỉ áp dụng loại ghế cụ thể thì chỉ tính phần tiền ghế đó.
     */
    private VoucherDiscount evaluateVoucher(String code, BookingSelection selection, List<TicketLine> tickets,
                                            BigDecimal ticketSubtotal, BigDecimal comboSubtotal,
                                            BigDecimal beforeDiscount, boolean strict) {
        String normalized = normalizeVoucherCode(code);
        if (normalized == null) {
            return new VoucherDiscount(null, BigDecimal.ZERO, null);
        }
        Voucher voucher = voucherRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher " + normalized + "."));
        String reason = voucherIneligibilityReason(voucher, selection, tickets, ticketSubtotal, comboSubtotal, beforeDiscount);
        if (reason != null) {
            if (strict) {
                throw new IllegalArgumentException(reason);
            }
            return new VoucherDiscount(normalized, BigDecimal.ZERO, reason);
        }
        BigDecimal eligibleAmount = eligibleVoucherAmount(voucher, tickets, ticketSubtotal, comboSubtotal);
        BigDecimal discount = calculateVoucherDiscount(voucher, eligibleAmount);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            reason = "Voucher chưa tạo ra giá trị giảm cho đơn tại quầy.";
            if (strict) {
                throw new IllegalArgumentException(reason);
            }
        }
        return new VoucherDiscount(normalized, discount, reason);
    }

    private String voucherIneligibilityReason(Voucher voucher, BookingSelection selection, List<TicketLine> tickets,
                                              BigDecimal ticketSubtotal, BigDecimal comboSubtotal,
                                              BigDecimal beforeDiscount) {
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(voucher.getIsDeleted())) return "Voucher đã ngừng hoạt động.";
        if (voucher.getStartDate() == null || voucher.getEndDate() == null) return "Voucher thiếu thời gian áp dụng.";
        if (voucher.getStartDate().isAfter(now)) return "Voucher chưa đến thời gian áp dụng.";
        if (!voucher.getEndDate().isAfter(now)) return "Voucher đã hết hạn.";
        if (voucher.getTotalQuantity() != null && voucher.getUsedQuantity() != null
                && voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            return "Voucher đã hết số lượng phát hành.";
        }
        if (beforeDiscount.compareTo(safeMoney(voucher.getMinOrderValue())) < 0) {
            return "Đơn hàng chưa đạt giá trị tối thiểu của voucher.";
        }
        if (!matchesApplicableDay(voucher.getApplicableDays(), selection.showDate())) {
            return "Voucher không áp dụng cho ngày chiếu đã chọn.";
        }
        if (Boolean.FALSE.equals(voucher.getIsHolidayApplicable()) && holidayRepository.existsByHolidayDate(selection.showDate())) {
            return "Voucher không áp dụng vào ngày lễ/ngày cao điểm.";
        }
        if (eligibleVoucherAmount(voucher, tickets, ticketSubtotal, comboSubtotal).compareTo(BigDecimal.ZERO) <= 0) {
            return "Đơn hàng không có dịch vụ phù hợp với voucher.";
        }
        if (voucher.getDiscountType() == null || voucher.getDiscountValue() == null
                || voucher.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            return "Voucher chưa có cấu hình giảm giá hợp lệ.";
        }
        return null;
    }

    private BigDecimal eligibleVoucherAmount(Voucher voucher, List<TicketLine> tickets,
                                             BigDecimal ticketSubtotal, BigDecimal comboSubtotal) {
        Voucher.ServiceScope scope = voucher.getServiceScope();
        if (scope == Voucher.ServiceScope.WATER) {
            return comboSubtotal;
        }
        BigDecimal eligibleTicketAmount = eligibleTicketAmount(voucher, tickets, ticketSubtotal);
        if (scope == Voucher.ServiceScope.TICKET) {
            return eligibleTicketAmount;
        }
        return eligibleTicketAmount.add(comboSubtotal);
    }

    private BigDecimal eligibleTicketAmount(Voucher voucher, List<TicketLine> tickets, BigDecimal ticketSubtotal) {
        Set<String> types = normalizedSeatTypes(voucher.getApplicableSeats());
        if (types.isEmpty()) {
            return ticketSubtotal;
        }
        return tickets.stream()
                .filter(ticket -> types.contains(normalizeSeatType(ticket.type())))
                .map(TicketLine::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    /**
     * Xác định account gắn với hóa đơn tại quầy.
     *
     * Thứ tự ưu tiên:
     * 1. Nếu nhân viên chọn khách thành viên từ autocomplete, dùng `customerAccountId`.
     * 2. Nếu nhập số điện thoại/email trùng account có sẵn, dùng account đó.
     * 3. Nếu không có thông tin hoặc không tìm thấy, dùng account kỹ thuật `walkin@counter.local`
     *    đại diện cho khách vãng lai tại quầy.
     */
    private Account resolveCustomerAccount(CounterSaleRequest request) {
        if (request.customerAccountId() != null) {
            return accountRepository.findById(request.customerAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng thành viên."));
        }
        String phone = trimToNull(request.customerPhone());
        if (phone != null) {
            Account found = accountRepository.findFirstByPhoneNum(phone).orElse(null);
            if (found != null) return found;
        }
        String email = trimToNull(request.customerEmail());
        if (email != null) {
            Account found = accountRepository.findByEmail(email);
            if (found != null) return found;
        }
        return ensureWalkInAccount();
    }

    private Account ensureWalkInAccount() {
        Account existing = accountRepository.findByEmail(WALK_IN_EMAIL);
        if (existing != null) {
            return existing;
        }
        Account account = new Account();
        account.setName("Khách vãng lai tại quầy");
        account.setEmail(WALK_IN_EMAIL);
        account.setPassword("counter-sale");
        account.setPhoneNum(uniqueWalkInPhone());
        account.setDob(LocalDate.of(2000, 1, 1));
        account.setGender("Khác");
        account.setAddress("Counter POS");
        account.setLoyaltyPoint(0);
        account.setMembershipLevel(MembershipLevel.BRONZE);
        account.setStatus(true);
        account.setRole(Role.CUSTOMER);
        return accountRepository.save(account);
    }

    private String uniqueWalkInPhone() {
        String base = "0999999998";
        if (!accountRepository.existsByPhoneNum(base)) {
            return base;
        }
        for (int i = 0; i < 100; i++) {
            String candidate = "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
            if (!accountRepository.existsByPhoneNum(candidate)) {
                return candidate;
            }
        }
        return "09" + String.valueOf(System.currentTimeMillis()).substring(5, 13);
    }

    /**
     * Tăng số lượng voucher đã dùng sau khi đơn thật được chốt.
     *
     * Repository dùng update có điều kiện để tránh vượt tổng số lượng phát hành
     * nếu nhiều quầy cùng dùng voucher ở cùng thời điểm.
     */
    private void markVoucherAsUsed(String voucherCode) {
        String normalized = normalizeVoucherCode(voucherCode);
        if (normalized == null) {
            return;
        }
        int updated = voucherRepository.incrementUsedQuantityIfAvailable(normalized);
        if (updated == 0) {
            throw new IllegalArgumentException("Voucher " + normalized + " đã hết số lượng phát hành.");
        }
    }

    /**
     * Tạo bản ghi vé hiển thị/in cho từng ghế đã bán.
     *
     * `booking_tickets` là bảng chống trùng ghế theo suất.
     * `ticket` là bảng phục vụ nghiệp vụ sau bán: my-tickets, QR, in vé, lịch sử vé.
     * Vì vậy sau khi thanh toán thành công, mỗi ghế BOOKED cần một bản ghi `Ticket`.
     */
    private void createDisplayTickets(Account customer, SaleDraft draft, List<BookingTicket> bookingTickets,
                                      Booking booking, Payment payment, CounterSaleRequest request) {
        Showtime showtime = showtimeRepository.findById(draft.selection().showtimeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));
        Map<Long, TicketLine> lineBySeat = new HashMap<>();
        draft.tickets().forEach(line -> lineBySeat.put(line.seatId(), line));
        String customerName = trimToNull(request.customerName());
        String customerPhone = trimToNull(request.customerPhone());
        for (BookingTicket bookingTicket : bookingTickets) {
            TicketLine line = lineBySeat.get(bookingTicket.getSeatId());
            Ticket ticket = new Ticket();
            ticket.setAccount(customer);
            ticket.setMovie(showtime.getMovie());
            ticket.setShowtime(showtime);
            ticket.setRoomName(showtime.getRoom());
            ticket.setSeatLabel(bookingTicket.getSeatLabel());
            ticket.setSeatNumber(bookingTicket.getSeatLabel());
            ticket.setSeatType(bookingTicket.getSeatType());
            ticket.setShowDate(showtime.getShowDate());
            ticket.setShowTime(showtime.getShowTime());
            ticket.setPrice(line != null ? line.price() : bookingTicket.getPrice());
            ticket.setBookingTime(booking.getCreatedAt());
            ticket.setCreatedAt(booking.getCreatedAt());
            ticket.setStatus("CONFIRMED");
            ticket.setCustomerType(normalizeCustomerType(request.customerType()));
            ticket.setCustomerName(customerName != null ? customerName : customer.getName());
            ticket.setCustomerPhone(customerPhone != null ? customerPhone : customer.getPhoneNum());
            ticket.setPaymentMethod(payment.getPaymentMethod().name());
            ticket.setBookingCode(payment.getOrderCode());
            ticketRepository.save(ticket);
        }
    }

    /**
     * Chuyển `showtimeId` thành context bán vé đầy đủ.
     *
     * Showtime trong project lưu tên phòng dạng text, nên service phải map ngược sang `Room`
     * qua `RoomRepository.findFirstByRoomName...` để lấy roomId và sơ đồ ghế đã cấu hình.
     */
    private BookingSelection selectionFor(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy suất chiếu."));
        Room room = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), DEFAULT_CINEMA_ID)
                .orElseGet(() -> roomRepository.findFirstByRoomNameIgnoreCase(showtime.getRoom())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng chiếu của suất này.")));
        int duration = showtime.getMovie() != null && showtime.getMovie().getDuration() != null
                ? showtime.getMovie().getDuration()
                : 120;
        return new BookingSelection(
                showtime.getId(),
                showtime.getMovie() != null ? showtime.getMovie().getId() : null,
                room.getId(),
                showtime.getMovie() != null ? showtime.getMovie().getTitle() : "Phim",
                room.getRoomName(),
                showtime.getShowDate(),
                showtime.getShowTime(),
                showtime.getShowTime().plusMinutes(duration),
                room.getRoomType()
        );
    }

    /** Chặn bán vé tại quầy cho suất đã qua giờ bắt đầu. */
    private void ensureShowtimeStillSellable(BookingSelection selection) {
        if (selection.showDate().isBefore(LocalDate.now())
                || (selection.showDate().isEqual(LocalDate.now()) && !selection.startTime().isAfter(LocalTime.now()))) {
            throw new IllegalArgumentException("Suất chiếu đã qua giờ, không thể bán vé tại quầy.");
        }
    }

    /**
     * Chuyển entity `Showtime` sang dữ liệu card suất chiếu trên màn POS.
     *
     * Hàm này tính nhanh:
     * - Sức chứa phòng từ `rooms.total_seats`.
     * - Ghế đã bán từ `booking_tickets` BOOKED.
     * - Ghế đang giữ còn hạn từ `booking_tickets` HOLDING.
     * - Ghế còn bán = capacity - booked - holding.
     */
    private ShowtimeOption toShowtimeOption(Showtime showtime) {
        Room room = roomRepository.findFirstByRoomNameIgnoreCaseAndCinemaId(showtime.getRoom(), DEFAULT_CINEMA_ID)
                .orElseGet(() -> roomRepository.findFirstByRoomNameIgnoreCase(showtime.getRoom()).orElse(null));
        int capacity = room != null ? room.getTotalSeats() : 0;
        List<BookingTicket> tickets = bookingTicketRepository.findByShowtimeId(showtime.getId());
        long booked = tickets.stream().filter(ticket -> ticket.getStatus() == BookingTicket.Status.BOOKED).count();
        long holding = tickets.stream().filter(ticket -> ticket.getStatus() == BookingTicket.Status.HOLDING
                && ticket.getHoldExpiresAt() != null
                && ticket.getHoldExpiresAt().isAfter(LocalDateTime.now())).count();
        int available = Math.max(0, capacity - (int) booked - (int) holding);
        return new ShowtimeOption(
                showtime.getId(),
                showtime.getMovie() != null ? showtime.getMovie().getId() : null,
                showtime.getMovie() != null ? showtime.getMovie().getTitle() : "Phim",
                showtime.getShowDate(),
                showtime.getShowTime(),
                showtime.getRoom(),
                room != null ? room.getRoomType() : "",
                showtime.getDayType(),
                capacity,
                (int) booked,
                available
        );
    }

    /**
     * Chuẩn hóa phương thức thanh toán từ frontend.
     *
     * Với bán tại quầy hiện tại, nghiệp vụ thực tế chỉ dùng:
     * - CASH: khách trả tiền mặt tại quầy.
     * - PAYOS: khách quét/chuyển khoản qua payOS/VietQR.
     */
    private Payment.Method resolvePaymentMethod(String method) {
        String normalized = method == null || method.isBlank() ? "CASH" : method.trim().toUpperCase(Locale.ROOT);
        try {
            return Payment.Method.valueOf(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Phương thức thanh toán tại quầy không hợp lệ.");
        }
    }

    private String generateCounterOrderCode() {
        return "POS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String generatePayOsOrderCode() {
        long epochSeconds = System.currentTimeMillis() / 1000;
        int suffix = ThreadLocalRandom.current().nextInt(10, 99);
        return String.valueOf(epochSeconds * 100 + suffix);
    }

    private String resolveCounterOrderCode(Payment.Method method, String requestedCode) {
        if (method == Payment.Method.CASH) {
            return generateCounterOrderCode();
        }
        String normalized = trimToNull(requestedCode);
        if (normalized == null) {
            return generateCounterOrderCode();
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", "");
        if (normalized.length() < 6) {
            return generateCounterOrderCode();
        }
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    private String paymentQrContent(Payment.Method method, String orderCode, BigDecimal total) {
        if (method == Payment.Method.CASH) {
            return null;
        }
        return "BETA CINEMAS|POS|" + method.name() + "|" + orderCode + "|" + total.setScale(0, RoundingMode.HALF_UP) + "VND";
    }

    private String normalizeCustomerType(String customerType) {
        if (customerType == null || customerType.isBlank()) {
            return "ADULT";
        }
        return customerType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeVoucherCode(String code) {
        if (code == null || code.isBlank()) return null;
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean matchesApplicableDay(Voucher.ApplicableDay applicableDay, LocalDate showDate) {
        if (applicableDay == null || applicableDay == Voucher.ApplicableDay.ALL || showDate == null) {
            return true;
        }
        DayOfWeek day = showDate.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        return applicableDay == Voucher.ApplicableDay.WEEKEND ? weekend : !weekend;
    }

    private Set<String> normalizedSeatTypes(String applicableSeats) {
        if (applicableSeats == null || applicableSeats.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(applicableSeats.split("[,;|]"))
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

    public record ShowtimeOption(Long id, Integer movieId, String movieTitle, LocalDate showDate, LocalTime showTime,
                                 String roomName, String format, String dayType, int capacity,
                                 int bookedSeats, int availableSeats) { }
    public record CustomerOption(Integer id, String name, String email, String phone, String membershipLevel) { }
    public record ComboOption(Long id, String name, String description, String image, BigDecimal price, String status) { }
    public record VoucherOption(Long id, String code, String title, String discountType, BigDecimal discountValue,
                                BigDecimal minOrderValue, LocalDateTime endDate) { }
    public record SeatMapResponse(BookingSelection selection, List<BookingSeatView> seats, int rows, int cols) { }
    public record HoldRequest(Long showtimeId, List<Long> seatIds, String holdToken) { }
    public record HoldResponse(String holdToken, LocalDateTime expiresAt, List<TicketLine> tickets) { }
    public record ComboSelection(Long comboId, Integer quantity) { }
    public record CounterSaleRequest(Long showtimeId, String holdToken, String customerType, Integer customerAccountId,
                                     String customerName, String customerPhone, String customerEmail,
                                     String voucherCode, String paymentMethod, String counterPaymentCode,
                                     List<ComboSelection> comboItems) { }
    public record TicketLine(Long seatId, String label, String type, BigDecimal price) { }
    public record ComboLine(Long id, String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) { }
    public record CounterSaleSummary(BookingSelection selection, List<TicketLine> tickets, List<ComboLine> combos,
                                     BigDecimal ticketSubtotal, BigDecimal comboSubtotal, BigDecimal discount,
                                     BigDecimal total, String voucherCode, String voucherMessage) { }
    public record CounterSaleResult(Long bookingId, String orderCode, String paymentMethod, BigDecimal total,
                                    List<String> seatLabels, boolean requiresCodeDisplay,
                                    String paymentQrContent, String message) { }
    public record CounterPaymentResult(Long bookingId, String orderCode, String paymentMethod, BigDecimal total,
                                       String checkoutUrl, String qrContent, String message) { }
    private record VoucherDiscount(String code, BigDecimal discount, String reason) { }
    private record SaleDraft(BookingSelection selection, List<TicketLine> tickets, List<ComboLine> combos,
                             BigDecimal ticketSubtotal, BigDecimal comboSubtotal, BigDecimal discount,
                             BigDecimal total, String voucherCode, String voucherMessage) {
        CounterSaleSummary toSummary() {
            return new CounterSaleSummary(selection, tickets, combos, ticketSubtotal, comboSubtotal,
                    discount, total, voucherCode, voucherMessage);
        }
    }
}
