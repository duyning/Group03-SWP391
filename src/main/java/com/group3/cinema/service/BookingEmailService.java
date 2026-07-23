/**
 * Service bất đồng bộ gửi Thư điện tử (Email) xác nhận đặt vé thành công cho Khách hàng (`BookingEmailService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được kích hoạt bất đồng bộ (`@Async`) bởi `CustomerBookingService` hoặc `PaymentService` ngay sau khi nhận thông báo thanh toán thành công từ cổng thanh toán.
 * - Gọi đến:
 *   + `CustomerBookingService`: Truy vấn thông tin chi tiết đơn hàng (`getBookingDetails`).
 *   + `AccountRepository`: Lấy địa chỉ email người mua.
 *   + `JavaMailSender`: Gửi thư điện tử xác nhận vé.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (26/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.BookingCombo;
import com.group3.cinema.entity.BookingFoodItem;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class BookingEmailService {
    private static final Logger log = LoggerFactory.getLogger(BookingEmailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final AccountRepository accountRepository;
    private final CustomerBookingService bookingService;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public BookingEmailService(JavaMailSender mailSender,
                               AccountRepository accountRepository,
                               CustomerBookingService bookingService) {
        this.mailSender = mailSender;
        this.accountRepository = accountRepository;
        this.bookingService = bookingService;
    }

    /**
     * Gửi email xác nhận thông tin vé và đơn hàng bất đồng bộ (`@Async`).
     * 
     * @param bookingId ID đơn đặt vé vừa thanh toán thành công.
     */
    @Async
    @Transactional(readOnly = true)
    public void sendTicketEmail(Long bookingId) {
        try {
            CustomerBookingService.BookingDetails details = bookingService.getBookingDetails(bookingId);
            Account account = accountRepository.findById(details.booking().getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Cannot find booking account."));

            if (account.getEmail() == null || account.getEmail().isBlank()) {
                log.warn("Skip booking email for booking {} because account {} has no email.",
                        bookingId, account.getAccountID());
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            message.setTo(account.getEmail());
            message.setSubject("CineFlow - Xac nhan dat ve #" + details.booking().getId());
            message.setText(buildMailBody(account, details));
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Could not send booking confirmation email for booking {}", bookingId, ex);
        }
    }

    /**
     * Xây dựng nội dung văn bản email xác nhận đặt vé bao gồm phim, suất chiếu, phòng, ghế, combo bắp nước và tổng tiền.
     */
    private String buildMailBody(Account account, CustomerBookingService.BookingDetails details) {
        String seats = details.tickets().stream()
                .map(BookingTicket::getSeatLabel)
                .collect(Collectors.joining(", "));
        String combos = details.combos().isEmpty()
                ? "Khong chon combo"
                : details.combos().stream()
                .map(this::comboLine)
                .collect(Collectors.joining("\n"));
        String foodItems = details.foodItems().isEmpty()
                ? "Khong chon mon le"
                : details.foodItems().stream()
                .map(this::foodItemLine)
                .collect(Collectors.joining("\n"));

        return """
                Xin chao %s,

                CineFlow da ghi nhan thanh toan thanh cong cho don dat ve #%d.

                Phim: %s
                Suat chieu: %s %s
                Phong: %s
                Ghe: %s

                Combo:
                %s

                Mon le:
                %s

                Tong thanh toan: %s

                Vui long dua email nay cho nhan vien rap khi can doi chieu thong tin ve.
                Cam on ban da dat ve tai CineFlow!
                """.formatted(
                safe(account.getName()),
                details.booking().getId(),
                details.showtime().getMovie().getTitle(),
                details.showtime().getShowDate().format(DATE_FORMAT),
                details.showtime().getShowTime().format(TIME_FORMAT),
                details.showtime().getRoom(),
                seats,
                combos,
                foodItems,
                money(details.booking().getTotalAmount())
        );
    }

    private String comboLine(BookingCombo combo) {
        return "- " + combo.getComboName() + " x" + combo.getQuantity() + ": " + money(combo.getSubtotal());
    }

    private String foodItemLine(BookingFoodItem item) {
        return "- " + item.getFoodItemName() + " x" + item.getQuantity() + ": " + money(item.getSubtotal());
    }

    private String money(Number value) {
        return NumberFormat.getInstance(new Locale("vi", "VN")).format(value) + " VND";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "ban" : value;
    }
}

