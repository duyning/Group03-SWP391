package com.group3.cinema.service;

/*
 * Added on 2026-06-24: Payment lifecycle for customer ticket booking.
 * Updated on 2026-06-26: Successful payments trigger booking confirmation email.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.Payment;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.PaymentRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.WishlistRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import com.group3.cinema.service.payment.PaymentGatewayRouter;
import com.group3.cinema.service.payment.PaymentGatewayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.group3.cinema.dto.BookingHistoryDto;
import com.group3.cinema.entity.Showtime;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingTicketRepository ticketRepository;
    private final BookingEmailService bookingEmailService;
    private final TicketRepository realTicketRepository;
    private final ShowtimeRepository showtimeRepository;
    private final AccountRepository accountRepository;
    private final VoucherRepository voucherRepository;
    private final WishlistRepository wishlistRepository;
    private final LoyaltyService loyaltyService;
    private final PaymentGatewayRouter gatewayRouter;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          BookingTicketRepository ticketRepository,
                          BookingEmailService bookingEmailService,
                          TicketRepository realTicketRepository,
                          ShowtimeRepository showtimeRepository,
                          AccountRepository accountRepository,
                          VoucherRepository voucherRepository,
                          WishlistRepository wishlistRepository,
                          LoyaltyService loyaltyService,
                          PaymentGatewayRouter gatewayRouter) {
        // Lưu các dependency do Spring inject; mọi cập nhật chính chạy trong transaction của service.
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.bookingEmailService = bookingEmailService;
        this.realTicketRepository = realTicketRepository;
        this.showtimeRepository = showtimeRepository;
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.wishlistRepository = wishlistRepository;
        this.loyaltyService = loyaltyService;
        this.gatewayRouter = gatewayRouter;
    }


    @Transactional
    public Payment createPayment(Long bookingId, Integer accountId, String method) {
        // Hệ thống hiện chỉ tích hợp PAYOS; mọi chuỗi method khác bị từ chối.
        if (method == null || !Payment.Method.PAYOS.name().equalsIgnoreCase(method.trim())) {
            throw new IllegalArgumentException("Chỉ hỗ trợ thanh toán qua payOS.");
        }

        // Kiểm tra owner, trạng thái PENDING và hạn booking.
        Booking booking = requirePayableBooking(bookingId, accountId);

        // Voucher có thể vừa hết số lượng từ lúc summary nên cần kiểm tra lại.
        ensureVoucherStillAvailable(booking);

        // Tìm lần thanh toán mới nhất của booking và chỉ tái sử dụng nếu vẫn PENDING.
        Payment existingPending = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .filter(payment -> payment.getStatus() == Payment.Status.PENDING)
                .orElse(null);

        // Chống double-click/tải lại tạo nhiều orderCode cho cùng booking.
        if (existingPending != null) {
            return existingPending;
        }

        // Tạo entity Payment mới.
        Payment payment = new Payment();

        // Gắn booking và gateway.
        payment.setBookingId(bookingId);
        payment.setPaymentMethod(Payment.Method.PAYOS);

        // orderCode dạng số để đáp ứng payOS.
        payment.setOrderCode(generatePayOsOrderCode());

        // Amount lấy từ totalAmount snapshot của booking, không lấy từ request.
        payment.setAmount(booking.getTotalAmount());

        // Chưa có callback nên trạng thái ban đầu PENDING.
        payment.setStatus(Payment.Status.PENDING);
        payment.setCreatedAt(LocalDateTime.now());

        // INSERT và trả Payment đã có ID.
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment processGatewayResult(String orderCode, boolean success, String responseCode,
                                        String transactionId, String message) {
        // Tìm Payment bằng orderCode do hệ thống đã tạo.
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán."));

        // Lấy Booking liên quan để cập nhật trạng thái và ghế.
        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));

        // Idempotency: webhook/return gọi lại sau lần đầu chỉ nhận kết quả đã chốt.
        if (payment.getStatus() != Payment.Status.PENDING) {
            return payment;
        }

        // Nhánh gateway xác nhận đã thanh toán.
        if (success) {
            // Đọc các dòng ghế đã gắn booking PENDING.
            List<BookingTicket> tickets = ticketRepository.findByBookingId(booking.getId());

            // Booking phải còn PENDING và còn dòng ghế để phát hành vé.
            if (booking.getStatus() != Booking.Status.PENDING || tickets.isEmpty()) {
                // Đánh dấu booking hết hạn và dọn ghế còn sót.
                expireBooking(booking);

                // Không thể phát hành vé dù tiền phía gateway báo thành công nên ghi FAILED để cần xử lý.
                payment.setStatus(Payment.Status.FAILED);
                payment.setResponseCode("EXPIRED");
                payment.setErrorMessage("Đơn đặt vé không còn ghế để phát hành sau khi thanh toán hoàn tất.");
                return paymentRepository.save(payment);
            }

            // Tăng used_quantity bằng update có điều kiện trước khi chốt giao dịch.
            markVoucherAsUsed(booking);

            // Ghi dữ liệu thành công từ gateway.
            payment.setStatus(Payment.Status.SUCCESS);
            payment.setResponseCode(responseCode);
            payment.setTransactionId(transactionId);
            payment.setPaidAt(LocalDateTime.now());

            // Booking chuyển trạng thái PAID và ghi paidAt.
            booking.setStatus(Booking.Status.PAID);
            booking.setPaidAt(LocalDateTime.now());

            // Chuyển từng ghế HOLDING thành BOOKED và xóa thông tin giữ tạm.
            tickets.forEach(ticket -> {
                ticket.setStatus(BookingTicket.Status.BOOKED);
                ticket.setHoldToken(null);
                ticket.setHoldExpiresAt(null);
            });

            // Batch persist trạng thái ghế.
            ticketRepository.saveAll(tickets);

            // Tạo vé điện tử thật trong bảng tickets và cộng điểm thành viên.
            saveRealTickets(booking, tickets, payment);
        } else if ("CANCELLED".equalsIgnoreCase(responseCode)) {
            // Nhánh khách hủy tại payOS.
            payment.setStatus(Payment.Status.CANCELLED);
            payment.setResponseCode(responseCode);

            // Hủy booking và xóa dòng ghế để trả chỗ.
            cancelBookingAndReleaseSeats(booking);

            // Dùng message gateway nếu có, ngược lại dùng thông báo mặc định.
            payment.setErrorMessage(message == null || message.isBlank() ? "Khách hàng hủy thanh toán." : message);
        } else if ("PENDING".equalsIgnoreCase(responseCode)) {
            // Gateway vẫn xử lý: giữ nguyên Payment/Booking PENDING.
            payment.setResponseCode(responseCode);
            payment.setErrorMessage(message);
        } else {
            // Mọi kết quả không success/cancel/pending được coi là FAILED.
            payment.setStatus(Payment.Status.FAILED);
            payment.setResponseCode(responseCode);
            payment.setErrorMessage(message == null || message.isBlank() ? "Giao dịch không thành công." : message);

            // Booking thất bại bị hủy và ghế được giải phóng.
            booking.setStatus(Booking.Status.CANCELLED);
            ticketRepository.deleteByBookingId(booking.getId());
        }

        // Persist Booking cho mọi nhánh đã thay đổi.
        bookingRepository.save(booking);

        // Persist Payment và giữ bản đã save để trả về.
        Payment savedPayment = paymentRepository.save(payment);

        // Chỉ SUCCESS mới kích hoạt gửi email.
        sendEmailIfPaid(savedPayment);

        // Trả trạng thái cuối cho controller/webhook.
        return savedPayment;
    }

    @Transactional
    public Payment getPayment(String orderCode, Integer accountId) {
        // Tìm giao dịch theo mã công khai.
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        // Xác minh booking của Payment thuộc đúng account.
        Booking booking = bookingRepository.findByIdAndAccountId(payment.getBookingId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền xem giao dịch này."));

        // Đồng bộ lỗi hết hạn/hủy nếu Payment vẫn PENDING.
        return synchronizePendingPayment(payment, booking);
    }

    @Transactional
    public Payment getPaymentPublic(String orderCode) {
        // Public return chỉ được phép tra theo orderCode khó đoán do hệ thống tạo.
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        // Bảo đảm booking tham chiếu vẫn tồn tại.
        bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));

        // Không tự expire: payOS có thể đã PAID nhưng webhook/API đang đến chậm.
        return payment;
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Payment reconcilePayOsPayment(String orderCode) {
        // Đọc Payment nội bộ trước khi gọi gateway.
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        // Booking cần cho kiểm tra hết hạn và xử lý kết quả.
        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));

        // Trạng thái đã chốt không cần gọi API lại.
        if (payment.getStatus() != Payment.Status.PENDING) {
            return payment;
        }

        // Router lấy PayOsGatewayService và gọi GET trạng thái trực tiếp tới payOS.
        PaymentGatewayService.GatewayPaymentStatus gatewayStatus = gatewayRouter
                .gateway(Payment.Method.PAYOS)
                .queryPayment(payment.getOrderCode());

        // Phản hồi phải có và đúng orderCode request.
        if (gatewayStatus == null || !payment.getOrderCode().equals(gatewayStatus.orderCode())) {
            throw new IllegalArgumentException("Mã giao dịch đối soát không khớp.");
        }

        // Amount gateway phải bằng amount snapshot nội bộ để chống nhầm/sửa giao dịch.
        if (gatewayStatus.amount() == null || payment.getAmount() == null
                || payment.getAmount().compareTo(gatewayStatus.amount()) != 0) {
            throw new IllegalArgumentException("Số tiền giao dịch đối soát không khớp.");
        }

        // Dùng cùng điểm hội tụ với webhook để cập nhật idempotent.
        Payment reconciled = processGatewayResult(
                gatewayStatus.orderCode(),
                gatewayStatus.success(),
                gatewayStatus.responseCode(),
                gatewayStatus.transactionId(),
                gatewayStatus.message()
        );

        // Nếu gateway vẫn PENDING thì kiểm tra booking nội bộ có hết hạn/hủy chưa.
        return reconciled.getStatus() == Payment.Status.PENDING
                ? synchronizePendingPayment(reconciled, booking)
                : reconciled;
    }

    @Transactional
    public Booking requirePayableBooking(Long id, Integer accountId) {
        // Query bằng cả ID và owner để không phân biệt "không tồn tại" với "không thuộc bạn".
        Booking booking = bookingRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt vé."));

        // Chỉ PENDING mới được bắt đầu/thử lại thanh toán.
        if (booking.getStatus() != Booking.Status.PENDING) {
            throw new IllegalArgumentException("Đơn này không thể thanh toán.");
        }

        // Booking quá hạn được cập nhật EXPIRED và trả ghế ngay trong request.
        if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireBooking(booking);
            throw new IllegalArgumentException("Đơn đặt vé đã hết hạn.");
        }

        // Trả booking đã xác minh cho createPayment/controller.
        return booking;
    }

    private void expireBooking(Booking booking) {
        // Đánh dấu trạng thái header.
        booking.setStatus(Booking.Status.EXPIRED);
        bookingRepository.save(booking);

        // Xóa các BookingTicket để ghế trở lại AVAILABLE.
        ticketRepository.deleteByBookingId(booking.getId());
    }

    private Payment synchronizePendingPayment(Payment payment, Booking booking) {
        // Payment đã chốt không cần đồng bộ nữa.
        if (payment.getStatus() != Payment.Status.PENDING) {
            return payment;
        }

        // Booking bị hủy nhưng Payment còn pending phải chuyển Payment CANCELLED.
        if (booking.getStatus() == Booking.Status.CANCELLED) {
            payment.setStatus(Payment.Status.CANCELLED);
            payment.setResponseCode("BOOKING_CANCELLED");
            payment.setErrorMessage("Đơn đặt vé đã bị hủy nên không phát hành vé.");
            return paymentRepository.save(payment);
        }

        // Xác định hết hạn từ status hoặc timestamp PENDING.
        boolean expired = booking.getStatus() == Booking.Status.EXPIRED
                || (booking.getStatus() == Booking.Status.PENDING
                && booking.getExpiresAt() != null
                && booking.getExpiresAt().isBefore(LocalDateTime.now()));

        // Đồng bộ booking/payment khi hết hạn.
        if (expired) {
            // Nếu scheduler chưa đánh dấu, thực hiện ngay tại đây.
            if (booking.getStatus() == Booking.Status.PENDING) {
                expireBooking(booking);
            }

            // Payment không thể tiếp tục vì ghế đã được trả.
            payment.setStatus(Payment.Status.FAILED);
            payment.setResponseCode("EXPIRED");
            payment.setErrorMessage("Đơn đặt vé đã hết hạn do quá thời gian thanh toán nên không phát hành vé.");
            return paymentRepository.save(payment);
        }

        // Chưa có thay đổi trạng thái.
        return payment;
    }

    private void cancelBookingAndReleaseSeats(Booking booking) {
        // Đánh dấu booking hủy.
        booking.setStatus(Booking.Status.CANCELLED);

        // Xóa dòng ghế để khách khác chọn.
        ticketRepository.deleteByBookingId(booking.getId());
    }

    private void ensureVoucherStillAvailable(Booking booking) {
        // Chuẩn hóa snapshot code; null nghĩa là booking không dùng voucher.
        String voucherCode = normalizeVoucherCode(booking.getVoucherCode());
        if (voucherCode == null) {
            return;
        }

        // Voucher legacy không tồn tại trong repository được bỏ qua; voucher managed phải còn quantity.
        voucherRepository.findByCodeIgnoreCase(voucherCode).ifPresent(voucher -> {
            // null usedQuantity được xem là 0.
            int usedQuantity = voucher.getUsedQuantity() == null ? 0 : voucher.getUsedQuantity();
            Integer totalQuantity = voucher.getTotalQuantity();

            // Chặn tạo payment link khi voucher đã hết.
            if (totalQuantity != null && usedQuantity >= totalQuantity) {
                throw new IllegalArgumentException("Voucher " + voucher.getCode()
                        + " đã hết số lượng phát hành. Vui lòng quay lại chọn voucher khác.");
            }
        });
    }

    private void markVoucherAsUsed(Booking booking) {
        // Booking không dùng voucher không cần cập nhật.
        String voucherCode = normalizeVoucherCode(booking.getVoucherCode());
        if (voucherCode == null) {
            return;
        }

        // Chỉ voucher được quản lý bởi bảng vouchers mới có used_quantity.
        boolean managedVoucher = voucherRepository.findByCodeIgnoreCase(voucherCode).isPresent();
        if (!managedVoucher) {
            return;
        }

        // Atomic UPDATE chỉ tăng khi used_quantity < total_quantity.
        int updatedRows = voucherRepository.incrementUsedQuantityIfAvailable(voucherCode);

        // 0 row nghĩa là transaction khác đã lấy lượt cuối hoặc voucher không còn hợp lệ.
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Voucher " + voucherCode
                    + " đã hết số lượng phát hành. Vui lòng quay lại chọn voucher khác.");
        }
    }

    private String normalizeVoucherCode(String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return null;
        }
        return voucherCode.trim().toUpperCase();
    }

    private String generatePayOsOrderCode() {
        // Epoch seconds tạo phần thời gian tăng dần.
        long epochSeconds = System.currentTimeMillis() / 1000;

        // Hai chữ số ngẫu nhiên giảm khả năng hai request cùng giây trùng mã.
        int suffix = ThreadLocalRandom.current().nextInt(10, 99);

        // payOS yêu cầu orderCode dạng số nên ghép bằng phép nhân/cộng rồi đổi chuỗi.
        return String.valueOf(epochSeconds * 100 + suffix);
    }

    private void sendEmailIfPaid(Payment payment) {
        // Chỉ gửi email khi transaction đã chuyển Payment SUCCESS.
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            bookingEmailService.sendTicketEmail(payment.getBookingId());
        }
    }

    private void saveRealTickets(Booking booking, List<BookingTicket> bookingTickets, Payment payment) {
        // Không thể hoàn tất thanh toán nếu không còn ghế để phát hành.
        if (bookingTickets == null || bookingTickets.isEmpty()) {
            throw new IllegalStateException("Cannot complete payment without booking tickets.");
        }

        // Tải Account đầy đủ để gắn quan hệ cho Ticket.
        var account = accountRepository.findById(booking.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Cannot find booking account."));

        // Tải Showtime để snapshot phim, phòng, ngày và giờ.
        var showtime = showtimeRepository.findById(booking.getShowtimeId())
                .orElseThrow(() -> new IllegalStateException("Cannot find booking showtime."));

        // Movie đã nằm trong quan hệ của Showtime.
        var movie = showtime.getMovie();

        // Mỗi BookingTicket (dòng giữ ghế) tạo một Ticket điện tử.
        for (BookingTicket bt : bookingTickets) {
            Ticket t = new Ticket();

            // Gắn owner và phim.
            t.setAccount(account);
            t.setMovie(movie);

            // Sao chép thông tin suất/ghế để vé không đổi khi catalog thay đổi.
            t.setRoomName(showtime.getRoom());
            t.setSeatLabel(bt.getSeatLabel());
            t.setSeatType(bt.getSeatType());
            t.setShowDate(showtime.getShowDate());
            t.setShowTime(showtime.getShowTime());
            t.setPrice(bt.getPrice());
            t.setBookingTime(booking.getCreatedAt());

            // Ticket phát hành sau thanh toán luôn CONFIRMED.
            t.setStatus("CONFIRMED");

            // Lưu phương thức và mã booking để tra cứu/hiển thị.
            t.setPaymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "PAYOS");
            t.setBookingCode(payment.getOrderCode() != null ? payment.getOrderCode() : "CF-" + booking.getId());

            // INSERT vé thật.
            realTicketRepository.save(t);
        }

        // Cộng điểm nghiêm ngặt; lỗi sẽ propagate để rollback toàn transaction thanh toán.
        loyaltyService.addLoyaltyPointsStrict(booking.getAccountId(), booking.getTotalAmount());
    }

    public void cleanWishlistIfFromWishlist(jakarta.servlet.http.HttpSession session, Payment payment) {
        // Hậu xử lý chỉ chạy khi có đủ session và Payment.
        if (session == null || payment == null) {
            return;
        }
        try {
            // Tìm booking liên quan, nếu không còn thì bỏ qua.
            bookingRepository.findById(payment.getBookingId()).ifPresent(booking -> {
                // Từ showtime xác định phim vừa mua.
                showtimeRepository.findById(booking.getShowtimeId()).ifPresent(showtime -> {
                    var movie = showtime.getMovie();
                    if (movie != null) {
                        // Cờ session được BookingController tạo khi URL có from=wishlist.
                        String attrName = "from_wishlist_movie_" + movie.getId();
                        Boolean fromWishlist = (Boolean) session.getAttribute(attrName);

                        // Chỉ dọn wishlist khi đúng cờ của phim này.
                        if (fromWishlist != null && fromWishlist) {
                            // Tìm item theo owner + movie.
                            wishlistRepository.findByAccountAccountIDAndMovieId(booking.getAccountId(), movie.getId())
                                    .ifPresent(item -> {
                                        // Xóa item wishlist sau khi mua thành công.
                                        wishlistRepository.delete(item);
                                        System.out.println("Success: Auto-removed movie " + movie.getTitle() + " from wishlist for account " + booking.getAccountId());
                                    });

                            // Xóa cờ để callback/tải lại không lặp thao tác.
                            session.removeAttribute(attrName);
                        }
                    }
                });
            });
        } catch (Exception ex) {
            // Wishlist là hậu xử lý; lỗi không được rollback/che khuất thanh toán đã thành công.
            System.err.println("Warning: Failed to clean wishlist for checkout: " + ex.getMessage());
        }
    }
    @Transactional
    /**
     * Tong hop Booking, Payment, Showtime va BookingTicket thanh lich su giao dich
     * danh rieng cho mot account.
     */
    public List<BookingHistoryDto> getBookingHistory(Integer accountId) {
        // Lay booking cua account va sap xep giao dich moi nhat truoc.
        List<Booking> bookings = bookingRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        // DTO tach du lieu hien thi khoi cac JPA Entity.
        List<BookingHistoryDto> dtos = new ArrayList<>();
        
        // Moi Booking se tao ra mot dong/card lich su.
        for (Booking booking : bookings) {
            // Khoi tao DTO rong cho booking hien tai.
            BookingHistoryDto dto = new BookingHistoryDto();
            // Thoi gian giao dich lay tu thoi diem tao Booking.
            dto.setBookingTime(booking.getCreatedAt());
            // Tong tien lay tu Booking.
            dto.setTotalAmount(booking.getTotalAmount());
            // Đọc trước khi đồng bộ hết hạn vì bước hết hạn sẽ giải phóng các dòng giữ ghế.
            List<BookingTicket> tickets = ticketRepository.findByBookingId(booking.getId());
            
            // Lay lan Payment moi nhat; co the null neu booking chua thanh toan.
            Payment payment = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(booking.getId()).orElse(null);
            if (payment != null) {
                if (payment.getStatus() == Payment.Status.PENDING
                        && payment.getPaymentMethod() == Payment.Method.PAYOS) {
                    try {
                        payment = reconcilePayOsPayment(payment.getOrderCode());
                    } catch (IllegalArgumentException ex) {
                        // Lỗi mạng/cấu hình payOS không phải bằng chứng giao dịch thất bại.
                        // Giữ PENDING để lần tải sau có thể đối soát lại và phát hành vé.
                    }
                } else {
                    payment = synchronizePendingPayment(payment, booking);
                }
            }
            
            // Khi co Payment, uu tien ma/phuong thuc/trang thai tu Payment.
            if (payment != null) {
                // orderCode null se duoc thay bang ma noi bo CF-{bookingId}.
                dto.setBookingCode(payment.getOrderCode() != null ? payment.getOrderCode() : "CF-" + booking.getId());
                // paymentMethod null se hien gia tri mac dinh tren UI.
                dto.setPaymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "Thẻ/Ví");
                
                // Doi enum Payment.Status thanh nhan va CSS class cho UI.
                switch (payment.getStatus()) {
                    // Thanh toan thanh cong.
                    case SUCCESS:
                        dto.setStatus("Thành công");
                        dto.setStatusClass("status-success");
                        break;
                    // Thanh toan that bai.
                    case FAILED:
                        dto.setStatus("Thất bại");
                        dto.setStatusClass("status-failed");
                        break;
                    // Thanh toan da bi huy.
                    case CANCELLED:
                        dto.setStatus("Đã hủy");
                        dto.setStatusClass("status-cancelled");
                        break;
                    // PENDING va cac gia tri con lai deu hien dang xu ly.
                    case PENDING:
                    default:
                        dto.setStatus("Đang xử lý");
                        dto.setStatusClass("status-pending");
                        break;
                }
            // Chua co Payment: suy ra thong tin tam tu chinh Booking.
            } else {
                // Tao ma giao dich tam tu booking ID.
                dto.setBookingCode("CF-" + booking.getId());
                dto.setPaymentMethod("Chưa chọn");
                
                // Doi Booking.Status thanh nhan hien thi.
                switch (booking.getStatus()) {
                    // CANCELLED va EXPIRED deu coi la da huy.
                    case CANCELLED:
                    case EXPIRED:
                        dto.setStatus("Đã hủy");
                        dto.setStatusClass("status-cancelled");
                        break;
                    // PENDING va cac gia tri con lai dang cho thanh toan.
                    case PENDING:
                    default:
                        dto.setStatus("Đang chờ thanh toán");
                        dto.setStatusClass("status-pending");
                        break;
                }
            }
            
            // Tai suat chieu de lay ten phim cho dong mo ta.
            Showtime showtime = showtimeRepository.findById(booking.getShowtimeId()).orElse(null);
            // Neu suat chieu hoac phim khong con thi dung chuoi thay the.
            String movieTitle = (showtime != null && showtime.getMovie() != null) ? showtime.getMovie().getTitle() : "Phim không xác định";
            if (!tickets.isEmpty()) {
                // Vi du ket qua: A1, A2, A3.
                String seats = tickets.stream().map(BookingTicket::getSeatLabel).collect(Collectors.joining(", "));
                dto.setSummary(String.format("Thanh toán %d vé xem phim \"%s\" (Ghế %s)", tickets.size(), movieTitle, seats));
            } else if (payment != null && payment.getStatus() == Payment.Status.SUCCESS) {
                dto.setSummary(String.format("Thanh toán đã thành công cho phim \"%s\" nhưng chưa tìm thấy dữ liệu vé. Vui lòng liên hệ rạp.", movieTitle));
            } else if (payment != null && payment.getStatus() == Payment.Status.PENDING) {
                dto.setSummary(String.format("Giao dịch cho phim \"%s\" đang chờ payOS xác nhận nên chưa phát hành vé.", movieTitle));
            } else {
                dto.setSummary(String.format("Giao dịch cho phim \"%s\" không hoàn tất nên không phát hành vé.", movieTitle));
            }
            
            // Them DTO da hoan chinh vao danh sach ket qua.
            dtos.add(dto);
        }
        
        // Controller se phan trang danh sach nay.
        return dtos;
    }
}
