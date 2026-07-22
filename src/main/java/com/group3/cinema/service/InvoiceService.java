package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingFoodItem;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingFoodItemRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.PaymentRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InvoiceService {
    private static final String WALK_IN_EMAIL = "walkin@counter.local";
    private static final String WALK_IN_PHONE_PREFIX = "099999";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingTicketRepository bookingTicketRepository;
    private final BookingComboRepository bookingComboRepository;
    private final BookingFoodItemRepository bookingFoodItemRepository;
    private final AccountRepository accountRepository;
    private final ShowtimeRepository showtimeRepository;

    public InvoiceService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          BookingTicketRepository bookingTicketRepository,
                          BookingComboRepository bookingComboRepository,
                          BookingFoodItemRepository bookingFoodItemRepository,
                          AccountRepository accountRepository,
                          ShowtimeRepository showtimeRepository) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.bookingTicketRepository = bookingTicketRepository;
        this.bookingComboRepository = bookingComboRepository;
        this.bookingFoodItemRepository = bookingFoodItemRepository;
        this.accountRepository = accountRepository;
        this.showtimeRepository = showtimeRepository;
    }

    @Transactional(readOnly = true)
    public InvoicePage searchInvoices(InvoiceFilter filter) {
        InvoiceFilter normalized = normalizeFilter(filter);
        List<Booking> bookings = bookingRepository.searchInvoices(
                trimToNull(normalized.keyword()),
                parseBookingId(normalized.keyword()),
                normalized.bookingStatus(),
                normalized.paymentStatus(),
                normalized.paymentMethod(),
                startOfDay(normalized.fromDate()),
                endOfDay(normalized.toDate())
        );
        List<InvoiceRow> allRows = filterInvoiceRows(toRows(bookings), normalized.bookingStatus());
        List<ShowtimeInvoiceGroup> allGroups = groupByShowtime(allRows);
        int pageSize = normalizedPageSize(normalized.size());
        int totalPages = Math.max(1, (int) Math.ceil((double) allGroups.size() / pageSize));
        int pageNumber = Math.min(normalizedPage(normalized.page()), totalPages);
        int fromIndex = Math.min((pageNumber - 1) * pageSize, allGroups.size());
        int toIndex = Math.min(fromIndex + pageSize, allGroups.size());
        List<ShowtimeInvoiceGroup> pageGroups = allGroups.subList(fromIndex, toIndex);
        List<InvoiceRow> pageRows = pageGroups.stream()
                .flatMap(group -> group.invoices().stream())
                .toList();
        return new InvoicePage(
                pageRows,
                pageGroups,
                summarizeStats(allRows),
                summarizeByPaymentMethod(allRows),
                pageNumber,
                totalPages,
                pageSize,
                allRows.size(),
                allGroups.size()
        );
    }

    @Transactional(readOnly = true)
    public InvoiceDetails getInvoiceDetails(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn."));
        Account account = accountRepository.findById(booking.getAccountId()).orElse(null);
        Showtime showtime = showtimeRepository.findById(booking.getShowtimeId()).orElse(null);
        List<BookingTicket> tickets = bookingTicketRepository.findByBookingId(booking.getId()).stream()
                .sorted(Comparator.comparing(BookingTicket::getSeatLabel))
                .toList();
        List<BookingCombo> combos = bookingComboRepository.findByBookingId(booking.getId());
        List<BookingFoodItem> foodItems = bookingFoodItemRepository.findByBookingId(booking.getId());
        List<Payment> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId());
        Payment latestPayment = payments.isEmpty() ? null : payments.get(0);
        return new InvoiceDetails(
                toRow(booking, account, showtime, latestPayment, tickets),
                booking,
                account,
                showtime,
                tickets,
                combos,
                foodItems,
                payments
        );
    }

    @Transactional
    public void cancelPendingInvoice(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn."));
        if (booking.getStatus() != Booking.Status.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể hủy hóa đơn đang chờ thanh toán.");
        }

        booking.setStatus(Booking.Status.CANCELLED);
        bookingRepository.save(booking);
        bookingTicketRepository.deleteByBookingId(booking.getId());

        Payment latestPayment = latestPayment(booking.getId());
        if (latestPayment != null && latestPayment.getStatus() == Payment.Status.PENDING) {
            latestPayment.setStatus(Payment.Status.CANCELLED);
            latestPayment.setResponseCode("ADMIN_CANCELLED");
            latestPayment.setErrorMessage(operationNote("Hủy hóa đơn bởi quản lý", reason));
            paymentRepository.save(latestPayment);
        }
    }

    @Transactional
    public void refundPaidInvoice(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn."));
        if (booking.getStatus() != Booking.Status.PAID) {
            throw new IllegalArgumentException("Chỉ có thể hoàn tiền hóa đơn đã thanh toán.");
        }

        Payment latestPayment = latestPayment(booking.getId());
        if (latestPayment == null || latestPayment.getStatus() != Payment.Status.SUCCESS) {
            throw new IllegalArgumentException("Không tìm thấy giao dịch thanh toán thành công để hoàn tiền.");
        }

        booking.setStatus(Booking.Status.CANCELLED);
        bookingRepository.save(booking);

        latestPayment.setStatus(Payment.Status.CANCELLED);
        latestPayment.setResponseCode("REFUNDED");
        latestPayment.setErrorMessage(operationNote("Hoàn tiền bởi quản lý", reason));
        paymentRepository.save(latestPayment);
    }

    @Transactional(readOnly = true)
    public byte[] exportInvoicesCsv(InvoiceFilter filter) {
        InvoiceFilter normalized = normalizeFilter(filter);
        List<Booking> bookings = bookingRepository.searchInvoices(
                trimToNull(normalized.keyword()),
                parseBookingId(normalized.keyword()),
                normalized.bookingStatus(),
                normalized.paymentStatus(),
                normalized.paymentMethod(),
                startOfDay(normalized.fromDate()),
                endOfDay(normalized.toDate())
        );
        List<InvoiceRow> rows = filterInvoiceRows(toRows(bookings), normalized.bookingStatus());
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Mã hóa đơn,Khách hàng,Số điện thoại,Email,Phim,Phòng,Ngày chiếu,Giờ chiếu,Ghế,Nguồn,Trạng thái hóa đơn,Trạng thái thanh toán,Phương thức,Tiền vé,Tiền combo,Tiền món lẻ,Giảm giá,Tổng tiền,Voucher,Ngày tạo,Ngày thanh toán,Ghi chú\n");
        for (InvoiceRow row : rows) {
            csv.append(csv(row.invoiceCode())).append(',')
                    .append(csv(row.customerName())).append(',')
                    .append(csv(row.customerPhone())).append(',')
                    .append(csv(row.customerEmail())).append(',')
                    .append(csv(row.movieTitle())).append(',')
                    .append(csv(row.roomName())).append(',')
                    .append(csv(row.showDate())).append(',')
                    .append(csv(row.showTime())).append(',')
                    .append(csv(row.seats())).append(',')
                    .append(csv(row.source())).append(',')
                    .append(csv(row.bookingStatusText())).append(',')
                    .append(csv(row.paymentStatusText())).append(',')
                    .append(csv(row.paymentMethodText())).append(',')
                    .append(csv(row.ticketSubtotal())).append(',')
                    .append(csv(row.comboSubtotal())).append(',')
                    .append(csv(row.foodSubtotal())).append(',')
                    .append(csv(row.discountAmount())).append(',')
                    .append(csv(row.totalAmount())).append(',')
                    .append(csv(row.voucherCode())).append(',')
                    .append(csv(row.createdAt())).append(',')
                    .append(csv(row.paidAt())).append(',')
                    .append(csv(row.operationNote()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<InvoiceRow> toRows(List<Booking> bookings) {
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, Payment> latestPayments = latestPaymentsByBooking(bookingIds);
        Map<Integer, Account> accounts = accountRepository.findAllById(
                        bookings.stream().map(Booking::getAccountId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Account::getAccountID, account -> account));
        Map<Long, Showtime> showtimes = showtimeRepository.findAllById(
                        bookings.stream().map(Booking::getShowtimeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Showtime::getId, showtime -> showtime));
        return bookings.stream()
                .map(booking -> toRow(booking, accounts.get(booking.getAccountId()),
                        showtimes.get(booking.getShowtimeId()), latestPayments.get(booking.getId()),
                        bookingTicketRepository.findByBookingId(booking.getId())))
                .toList();
    }

    private List<InvoiceRow> filterInvoiceRows(List<InvoiceRow> rows, Booking.Status requestedStatus) {
        return rows.stream()
                .filter(row -> isRealInvoice(row, requestedStatus))
                .toList();
    }

    private boolean isRealInvoice(InvoiceRow row, Booking.Status requestedStatus) {
        if (requestedStatus == Booking.Status.PAID) {
            return row.bookingStatus() == Booking.Status.PAID && !row.refunded();
        }
        if (requestedStatus == Booking.Status.CANCELLED) {
            return row.refunded();
        }
        if (requestedStatus == Booking.Status.PENDING || requestedStatus == Booking.Status.EXPIRED) {
            return false;
        }
        return (row.bookingStatus() == Booking.Status.PAID && !row.refunded()) || row.refunded();
    }

    private InvoiceStats summarizeStats(List<InvoiceRow> rows) {
        long paidCount = rows.stream().filter(row -> row.bookingStatus() == Booking.Status.PAID && !row.refunded()).count();
        long refundedCount = rows.stream().filter(InvoiceRow::refunded).count();
        BigDecimal paidRevenue = rows.stream()
                .filter(row -> row.bookingStatus() == Booking.Status.PAID && !row.refunded())
                .map(InvoiceRow::totalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = rows.stream()
                .filter(InvoiceRow::refunded)
                .map(InvoiceRow::totalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoiceStats(paidCount, refundedCount, paidRevenue, refundedAmount);
    }

    private Map<Long, Payment> latestPaymentsByBooking(List<Long> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Payment> latest = new HashMap<>();
        paymentRepository.findByBookingIdIn(bookingIds).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .forEach(payment -> latest.putIfAbsent(payment.getBookingId(), payment));
        return latest;
    }

    private List<PaymentMethodSummary> summarizeByPaymentMethod(List<InvoiceRow> rows) {
        Map<Payment.Method, PaymentMethodSummaryBuilder> summaries = new EnumMap<>(Payment.Method.class);
        for (InvoiceRow row : rows) {
            Payment.Method method = normalizedBusinessPaymentMethod(row.paymentMethod());
            if (method == null || row.refunded() || row.bookingStatus() != Booking.Status.PAID) {
                continue;
            }
            summaries.computeIfAbsent(method, ignored -> new PaymentMethodSummaryBuilder())
                    .add(row.totalAmount());
        }
        return summaries.entrySet().stream()
                .map(entry -> new PaymentMethodSummary(entry.getKey(), entry.getValue().count, entry.getValue().amount))
                .sorted(Comparator.comparing(summary -> summary.method().name()))
                .toList();
    }

    private List<ShowtimeInvoiceGroup> groupByShowtime(List<InvoiceRow> rows) {
        Map<String, List<InvoiceRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(InvoiceRow::showtimeKey, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .map(groupRows -> {
                    InvoiceRow first = groupRows.get(0);
                    BigDecimal ticketRevenue = groupRows.stream()
                            .filter(row -> !row.refunded())
                            .map(InvoiceRow::ticketSubtotal)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal comboRevenue = groupRows.stream()
                            .filter(row -> !row.refunded())
                            .map(InvoiceRow::comboSubtotal)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal foodRevenue = groupRows.stream()
                            .filter(row -> !row.refunded())
                            .map(InvoiceRow::foodSubtotal)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalRevenue = groupRows.stream()
                            .filter(row -> !row.refunded())
                            .map(InvoiceRow::totalAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal refundedAmount = groupRows.stream()
                            .filter(InvoiceRow::refunded)
                            .map(InvoiceRow::totalAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long ticketCount = groupRows.stream()
                            .filter(row -> !row.refunded())
                            .mapToLong(InvoiceRow::seatCount)
                            .sum();
                    long refundedCount = groupRows.stream().filter(InvoiceRow::refunded).count();
                    return new ShowtimeInvoiceGroup(
                            first.showtimeKey(),
                            first.movieTitle(),
                            first.roomName(),
                            first.showDate(),
                            first.showTime(),
                            groupRows.size(),
                            ticketCount,
                            refundedCount,
                            ticketRevenue,
                            comboRevenue,
                            foodRevenue,
                            totalRevenue,
                            refundedAmount,
                            groupRows
                    );
                })
                .sorted(Comparator.comparing(ShowtimeInvoiceGroup::showDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ShowtimeInvoiceGroup::showTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ShowtimeInvoiceGroup::movieTitle))
                .toList();
    }

    private InvoiceRow toRow(Booking booking, Account account, Showtime showtime,
                             Payment payment, List<BookingTicket> tickets) {
        String movieTitle = showtime != null && showtime.getMovie() != null ? showtime.getMovie().getTitle() : "Không xác định";
        String roomName = showtime != null ? showtime.getRoom() : "Không xác định";
        LocalDate showDate = showtime != null ? showtime.getShowDate() : null;
        LocalTime showTime = showtime != null ? showtime.getShowTime() : null;
        String seats = tickets == null || tickets.isEmpty()
                ? ""
                : tickets.stream().map(BookingTicket::getSeatLabel).sorted().collect(Collectors.joining(", "));
        String source = payment != null && payment.getPaymentMethod() == Payment.Method.CASH ? "Tại quầy" : "Online";
        boolean refunded = payment != null && "REFUNDED".equalsIgnoreCase(payment.getResponseCode());
        return new InvoiceRow(
                booking.getId(),
                payment != null ? payment.getOrderCode() : "INV-" + booking.getId(),
                booking.getShowtimeId(),
                displayCustomerName(account),
                displayCustomerPhone(account),
                displayCustomerEmail(account),
                movieTitle,
                roomName,
                showDate,
                showTime,
                seats,
                booking.getTicketSubtotal(),
                booking.getComboSubtotal(),
                booking.getFoodSubtotal(),
                booking.getDiscountAmount(),
                booking.getTotalAmount(),
                booking.getVoucherCode(),
                booking.getStatus(),
                payment != null ? payment.getStatus() : null,
                normalizedBusinessPaymentMethod(payment != null ? payment.getPaymentMethod() : null),
                source,
                refunded,
                payment != null ? payment.getErrorMessage() : null,
                booking.getCreatedAt(),
                booking.getPaidAt()
        );
    }

    private String displayCustomerName(Account account) {
        if (account == null || isWalkInAccount(account)) {
            return "Khách vãng lai";
        }
        return trimToNull(account.getName()) == null ? "Khách hàng" : account.getName();
    }

    private String displayCustomerPhone(Account account) {
        if (account == null || isWalkInAccount(account)) {
            return "";
        }
        return trimToNull(account.getPhoneNum()) == null ? "" : account.getPhoneNum();
    }

    private String displayCustomerEmail(Account account) {
        if (account == null || isWalkInAccount(account)) {
            return "";
        }
        return trimToNull(account.getEmail()) == null ? "" : account.getEmail();
    }

    private boolean isWalkInAccount(Account account) {
        String email = trimToNull(account.getEmail());
        String name = trimToNull(account.getName());
        String phone = trimToNull(account.getPhoneNum());
        return (email != null && email.equalsIgnoreCase(WALK_IN_EMAIL))
                || (name != null && name.toLowerCase().contains("khách vãng lai"))
                || (phone != null && phone.startsWith(WALK_IN_PHONE_PREFIX));
    }

    private Payment latestPayment(Long bookingId) {
        List<Payment> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        return payments.isEmpty() ? null : payments.get(0);
    }

    private String operationNote(String action, String reason) {
        String normalizedReason = trimToNull(reason);
        String note = normalizedReason == null ? action : action + ": " + normalizedReason;
        return note.length() <= 450 ? note : note.substring(0, 450);
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long parseBookingId(String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null || !normalized.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private InvoiceFilter normalizeFilter(InvoiceFilter filter) {
        if (filter == null) {
            return new InvoiceFilter(null, null, null, null, null, null, 1, 20);
        }
        return new InvoiceFilter(filter.keyword(), filter.bookingStatus(), filter.paymentStatus(),
                filter.paymentMethod(), filter.fromDate(), filter.toDate(),
                normalizedPage(filter.page()), normalizedPageSize(filter.size()));
    }

    private int normalizedPage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizedPageSize(Integer size) {
        if (size == null || size < 5) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private static class PaymentMethodSummaryBuilder {
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;

        private PaymentMethodSummaryBuilder add(BigDecimal value) {
            count++;
            if (value != null) {
                amount = amount.add(value);
            }
            return this;
        }
    }

    public record InvoiceFilter(String keyword, Booking.Status bookingStatus,
                                Payment.Status paymentStatus, Payment.Method paymentMethod,
                                LocalDate fromDate, LocalDate toDate,
                                Integer page, Integer size) { }

    public record InvoicePage(List<InvoiceRow> rows, List<ShowtimeInvoiceGroup> showtimeGroups, InvoiceStats stats,
                              List<PaymentMethodSummary> paymentMethodSummaries,
                              int currentPage, int totalPages, int pageSize, long totalRows, long totalGroups) {
        public boolean hasPrevious() {
            return currentPage > 1;
        }

        public boolean hasNext() {
            return currentPage < totalPages;
        }

        public List<Integer> pageNumbers() {
            int start = Math.max(1, currentPage - 2);
            int end = Math.min(totalPages, currentPage + 2);
            List<Integer> pages = new ArrayList<>();
            for (int page = start; page <= end; page++) {
                pages.add(page);
            }
            return pages;
        }
    }

    public record InvoiceStats(long paidCount, long refundedCount,
                               BigDecimal paidRevenue, BigDecimal refundedAmount) { }

    public record PaymentMethodSummary(Payment.Method method, long count, BigDecimal amount) {
        public String methodText() {
            return paymentMethodText(method);
        }
    }

    public record ShowtimeInvoiceGroup(String key, String movieTitle, String roomName,
                                       LocalDate showDate, LocalTime showTime,
                                       long invoiceCount, long ticketCount, long refundedCount,
                                       BigDecimal ticketRevenue, BigDecimal comboRevenue, BigDecimal foodRevenue,
                                       BigDecimal totalRevenue, BigDecimal refundedAmount,
                                       List<InvoiceRow> invoices) { }

    public record InvoiceRow(Long bookingId, String invoiceCode, Long showtimeId, String customerName,
                             String customerPhone, String customerEmail, String movieTitle,
                             String roomName, LocalDate showDate, LocalTime showTime,
                             String seats, BigDecimal ticketSubtotal, BigDecimal comboSubtotal, BigDecimal foodSubtotal,
                             BigDecimal discountAmount, BigDecimal totalAmount, String voucherCode,
                             Booking.Status bookingStatus, Payment.Status paymentStatus,
                             Payment.Method paymentMethod, String source,
                             boolean refunded, String operationNote,
                             LocalDateTime createdAt, LocalDateTime paidAt) {
        public String bookingStatusText() {
            if (refunded) {
                return "Đã hoàn tiền";
            }
            return switch (bookingStatus) {
                case PAID -> "Đã thanh toán";
                case PENDING -> "Chờ thanh toán";
                case CANCELLED -> "Đã hủy";
                case EXPIRED -> "Hết hạn";
            };
        }

        public String paymentStatusText() {
            if (paymentStatus == null) {
                return "";
            }
            return switch (paymentStatus) {
                case SUCCESS -> "Thành công";
                case PENDING -> "Chờ thanh toán";
                case FAILED -> "Thất bại";
                case CANCELLED -> refunded ? "Đã hoàn tiền" : "Đã hủy";
            };
        }

        public String paymentMethodText() {
            return InvoiceService.paymentMethodText(paymentMethod);
        }

        public boolean hasCustomerContact() {
            return (customerPhone != null && !customerPhone.isBlank())
                    || (customerEmail != null && !customerEmail.isBlank());
        }

        public String showtimeKey() {
            return String.valueOf(showtimeId == null ? bookingId : showtimeId);
        }

        public long seatCount() {
            if (seats == null || seats.isBlank()) {
                return 0;
            }
            return List.of(seats.split("\\s*,\\s*")).stream()
                    .filter(value -> !value.isBlank())
                    .count();
        }
    }

    private static String paymentMethodText(Payment.Method method) {
        Payment.Method normalizedMethod = normalizedBusinessPaymentMethod(method);
        if (normalizedMethod == null) {
            return "";
        }
        return switch (normalizedMethod) {
            case CASH -> "Tiền mặt";
            case PAYOS -> "PayOS";
            default -> "PayOS";
        };
    }

    private static Payment.Method normalizedBusinessPaymentMethod(Payment.Method method) {
        if (method == null) {
            return null;
        }
        return method == Payment.Method.CASH ? Payment.Method.CASH : Payment.Method.PAYOS;
    }

    public record InvoiceDetails(InvoiceRow row, Booking booking, Account account,
                                 Showtime showtime, List<BookingTicket> tickets,
                                 List<BookingCombo> combos, List<BookingFoodItem> foodItems,
                                 List<Payment> payments) { }
}
