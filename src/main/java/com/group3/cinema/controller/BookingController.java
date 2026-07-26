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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Controller
@RequestMapping("/booking")
public class BookingController {

    // Tên key thống nhất để mọi bước booking cùng đọc/ghi một BookingSelection đã được server xác thực.
    public static final String BOOKING_SELECTION_SESSION_KEY = "bookingSelection";

    // Voucher được lưu bằng ID nội bộ; không dùng code do người dùng có thể sửa request.
    private static final String SELECTED_VOUCHER_ID_SESSION_KEY = "selectedVoucherId";

    // Service phụ trách phim, lịch chiếu, phòng và việc tạo BookingSelection.
    private final BookingShowtimeService bookingShowtimeService;

    // Service phụ trách sơ đồ, giá ghế và vòng đời giữ ghế tạm thời.
    private final SeatHoldingService seatHoldingService;

    // Service phụ trách combo, món lẻ, voucher, tổng tiền và tạo booking PENDING.
    private final CustomerBookingService customerBookingService;

    public BookingController(BookingShowtimeService bookingShowtimeService,
                             SeatHoldingService seatHoldingService,
                             CustomerBookingService customerBookingService) {
        // Constructor injection làm các dependency bắt buộc và dễ mock khi viết unit test.
        this.bookingShowtimeService = bookingShowtimeService;
        this.seatHoldingService = seatHoldingService;
        this.customerBookingService = customerBookingService;
    }

    @GetMapping("/showtimes")
    /**
     * Màn chọn suất chiếu: tải lịch còn bán trong 30 ngày và chọn ngày đầu tiên hợp lệ.
     * Cờ {@code from=wishlist} được lưu theo từng phim để sau thanh toán có thể tự dọn wishlist.
     */
    public String selectShowtime(@RequestParam("movieId") int movieId,
                                 @RequestParam(value = "date", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(value = "from", required = false) String from,
                                 HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Đọc và xác minh phim đang active, đồng thời chỉ cho NOW_SHOWING/SPECIAL_SCREENING mở bán.
            Movie movie = bookingShowtimeService.getBookableMovie(movieId);

            // Ghi dấu booking bắt đầu từ wishlist để sau thanh toán thành công có thể tự xóa phim khỏi wishlist.
            if ("wishlist".equalsIgnoreCase(from)) {
                session.setAttribute("from_wishlist_movie_" + movieId, true);
            } else {
                // Xóa dấu cũ nếu lần mở hiện tại không đi từ wishlist.
                session.removeAttribute("from_wishlist_movie_" + movieId);
            }

            // Service tải suất trong 30 ngày, loại suất quá giờ/hết ghế và gom theo ngày.
            var schedule = bookingShowtimeService.getAvailableShowtimeSchedule(movieId);

            // Ngày mặc định là ngày đầu tiên thực sự có suất còn bán.
            LocalDate firstShowDate = schedule.stream().findFirst()
                    .map(com.group3.cinema.dto.BookingShowtimeDateView::date)
                    // Nếu chưa có suất, dùng hôm nay để UI vẫn có giá trị ngày hợp lệ.
                    .orElse(LocalDate.now());

            // Chỉ giữ query parameter date nếu ngày đó thực sự có trong schedule do server tạo.
            LocalDate selectedDate = date != null && schedule.stream().anyMatch(day -> day.date().equals(date))
                    ? date : firstShowDate;

            // Header dùng Account trong session; null biểu thị khách chưa đăng nhập.
            model.addAttribute("user", (Account) session.getAttribute("loggedInUser"));

            // Movie cung cấp poster, tiêu đề và metadata ở cột bên trái.
            model.addAttribute("movie", movie);

            // selectedDate quyết định tab/panel ngày nào active khi trang được render.
            model.addAttribute("selectedDate", selectedDate);

            // schedule là danh sách ngày, mỗi ngày chứa các BookingShowtimeView.
            model.addAttribute("schedule", schedule);

            // Tên rạp được đọc từ bảng booking_settings.
            model.addAttribute("cinemaName", bookingShowtimeService.getCinemaName());

            // Render templates/booking-showtime.html.
            return "booking-showtime";
        } catch (IllegalArgumentException ex) {
            // Mọi lỗi phim/suất nghiệp vụ được hiển thị một lần sau redirect.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/movies";
        }
    }

    @PostMapping("/showtimes/select")
    /**
     * Xác nhận suất chiếu và tạo {@link BookingSelection} an toàn để lưu trong session.
     * Khi đổi suất, mọi ghế giữ và dữ liệu ở các bước sau phải được xóa để tránh dùng chéo đơn.
     */
    public String confirmShowtime(@RequestParam("showtimeId") long showtimeId,
                                  @RequestParam("movieId") int movieId,
                                  @RequestParam("date")
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            // Không tin ba hidden input: service đọc lại Showtime và kiểm tra quan hệ phim-ngày-phòng.
            BookingSelection selection = bookingShowtimeService.validateAndCreateSelection(
                    showtimeId, movieId, date);

            // Trả ghế của suất cũ trước khi chuyển sang suất mới.
            seatHoldingService.releaseHold((String) session.getAttribute("seatHoldToken"));

            // Dọn dữ liệu các bước sau để combo/voucher/booking cũ không đi theo suất mới.
            clearBookingSteps(session);

            // Lưu DTO bất biến thay vì lưu JPA entity có thể detached trong session.
            session.setAttribute(BOOKING_SELECTION_SESSION_KEY, selection);

            // Dùng redirect để URL chuyển sang bước 2 và tránh submit lại form khi refresh.
            return "redirect:/booking/seats";
        } catch (IllegalArgumentException ex) {
            // Giữ lỗi qua redirect về đúng phim/ngày để người dùng chọn một suất khác.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/showtimes?movieId=" + movieId + "&date=" + date;
        }
    }

    @GetMapping("/seats")
    /**
     * Màn chọn ghế: dựng sơ đồ ghế theo cấu hình phòng và trạng thái giữ/đặt hiện tại.
     * {@code seatHoldToken} giúp service phân biệt ghế do chính session này đang giữ.
     */
    public String seatSelection(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        // Selection chỉ được tạo bởi confirmShowtime sau khi backend xác thực suất chiếu.
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);

        // Chặn truy cập trực tiếp /booking/seats khi chưa hoàn tất bước chọn suất.
        if (selection == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn suất chiếu trước khi chọn ghế.");
            return "redirect:/movies";
        }

        // Truyền user cho header.
        model.addAttribute("user", (Account) session.getAttribute("loggedInUser"));

        // Truyền thông tin phim/suất/phòng cho phần tóm tắt.
        model.addAttribute("selection", selection);
        try {
            // Kết hợp sơ đồ ghế với trạng thái booking_tickets của đúng suất;
            // own token giúp phân biệt SELECTED của mình với HOLDING của khách khác.
            var seats = seatHoldingService.getSeatMap(selection, (String) session.getAttribute("seatHoldToken"));

            // Danh sách BookingSeatView được th:each dựng thành CSS Grid.
            model.addAttribute("seats", seats);

            // Metadata loại ghế dùng để dựng chú giải màu và sức chứa.
            model.addAttribute("seatTypes", seatHoldingService.getActiveSeatTypes());

            // Tính số cột cần thiết từ vị trí cuối cùng; capacity > 1 làm ghế đôi chiếm nhiều cột.
            model.addAttribute("roomCols", seats.stream()
                    .mapToInt(s -> s.colIndex() + Math.max(1, s.capacity()))
                    .max().orElse(10));

            // Nếu phiên đang có hold, thời điểm này được JavaScript bộ đếm sử dụng.
            model.addAttribute("holdExpiresAt", session.getAttribute("seatHoldExpiresAt"));

            // Render templates/seat-selection.html.
            return "seat-selection";
        } catch (IllegalArgumentException ex) {
            // Phòng/suất không còn hợp lệ thì quay về chọn suất thay vì render sơ đồ sai.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/showtimes?movieId=" + selection.movieId() + "&date=" + selection.showDate();
        }
    }

    @GetMapping("/seats/status")
    @ResponseBody
    /** Endpoint polling để trình duyệt cập nhật trạng thái ghế mà không tải lại toàn trang. */
    public List<BookingSeatView> seatStatus(HttpSession session) {
        // Polling vẫn phải dựa trên selection do server lưu, không nhận showtimeId tùy ý từ browser.
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);

        // Trả lỗi nếu session hết hoặc người dùng gọi endpoint sai thứ tự.
        if (selection == null) {
            throw new IllegalArgumentException("Vui lòng chọn suất chiếu trước.");
        }

        // Spring tự serialize List<BookingSeatView> thành JSON vì method có @ResponseBody.
        return seatHoldingService.getSeatMap(selection, (String) session.getAttribute("seatHoldToken"));
    }

    @PostMapping("/seats")
    /**
     * Giữ tạm các ghế được chọn trong 5 phút rồi chuyển sang bước đồ ăn.
     * Việc chọn lại ghế làm mất combo, món lẻ và voucher cũ vì tổng tiền đã thay đổi.
     */
    public String holdSeats(@RequestParam(value = "seatIds", required = false) List<Long> seatIds,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        // Lấy selection và đồng thời chuẩn bị flash error nếu bước trước bị thiếu.
        BookingSelection selection = requireSelection(session, redirectAttributes);

        // Không có selection thì luồng booking không đủ dữ liệu để giữ ghế.
        if (selection == null) return "redirect:/movies";
        try {
            // Service validate ID, phòng, loại ghế, sức chứa, tranh chấp và tạo hold trong transaction.
            SeatHoldingService.HoldResult result = seatHoldingService.holdSeats(selection, seatIds,
                    (String) session.getAttribute("seatHoldToken"));

            // Token nhóm các dòng ghế HOLDING thuộc cùng phiên trình duyệt.
            session.setAttribute("seatHoldToken", result.token());

            // Lưu hạn để các màn sau hiển thị đồng hồ đếm ngược.
            session.setAttribute("seatHoldExpiresAt", result.expiresAt());

            // Ghế thay đổi làm tổng tiền thay đổi, nên lựa chọn đồ ăn cũ phải được dọn.
            session.removeAttribute("selectedCombos");
            session.removeAttribute("selectedFoodItems");

            // Voucher cũ cũng phải tính lại vì điều kiện tiền vé/loại ghế có thể đã đổi.
            clearSelectedVoucher(session);

            // Chuyển sang bước 3.
            return "redirect:/booking/combos";
        } catch (IllegalArgumentException ex) {
            // Xung đột ghế/validation được báo ngay trên màn sơ đồ.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    @GetMapping("/seats/change")
    public String changeSeats(HttpSession session, RedirectAttributes redirectAttributes) {
        // Bắt buộc vẫn còn phim/suất để biết phải tải lại sơ đồ nào.
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";

        // Xóa các BookingTicket HOLDING của token hiện tại.
        seatHoldingService.releaseHold((String) session.getAttribute("seatHoldToken"));

        // Dọn toàn bộ dữ liệu phụ thuộc vào nhóm ghế cũ.
        session.removeAttribute("seatHoldToken");
        session.removeAttribute("seatHoldExpiresAt");
        session.removeAttribute("selectedCombos");
        session.removeAttribute("selectedFoodItems");
        clearSelectedVoucher(session);

        // Thông báo xác nhận ghế cũ đã được trả.
        redirectAttributes.addFlashAttribute("success", "Đã thả ghế cũ. Vui lòng chọn lại ghế.");
        return "redirect:/booking/seats";
    }

    @GetMapping("/combos")
    /**
     * Màn chọn combo và món lẻ. Summary tại bước này chủ yếu cung cấp tiền vé,
     * danh sách ghế và hạn giữ ghế; giá đồ ăn được JavaScript cập nhật tức thời.
     */
    public String selectCombos(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        // Không cho mở bước combo nếu chưa có suất.
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";
        try {
            // Tính summary cũng là bước xác nhận holdToken còn tồn tại và chưa hết hạn.
            CustomerBookingService.BookingSummary summary = customerBookingService.calculateSummary(
                    selection, (String) session.getAttribute("seatHoldToken"), selectedCombos(session),
                    selectedFoodItems(session), null);

            // Các model attribute dưới đây cấp dữ liệu cho header, catalog và số lượng đã chọn.
            model.addAttribute("user", session.getAttribute("loggedInUser"));
            model.addAttribute("selection", selection);
            model.addAttribute("combos", customerBookingService.getActiveCombos());
            model.addAttribute("foodItems", customerBookingService.getActiveFoodItems());
            model.addAttribute("selectedCombos", selectedCombos(session));
            model.addAttribute("selectedFoodItems", selectedFoodItems(session));
            model.addAttribute("summary", summary);

            // Render templates/booking-combo.html.
            return "booking-combo";
        } catch (IllegalArgumentException ex) {
            // Hold hết hạn thì quay về chọn ghế để tạo hold mới.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    @PostMapping("/combos")
    /**
     * Kiểm tra lại ID, trạng thái bán và số lượng của combo/món lẻ ở phía server.
     * Không tin trực tiếp dữ liệu giá từ form; giá thật luôn được đọc lại từ cơ sở dữ liệu.
     */
    public String saveCombos(@RequestParam Map<String, String> params, HttpSession session,
                             RedirectAttributes redirectAttributes) {
        // Đọc selection trực tiếp để có thể trả HTTP 400 cho request POST sai thứ tự.
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);
        if (selection == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn suất chiếu trước khi chọn combo.");
        }
        try {
            // Gọi summary rỗng để xác nhận selection và hold vẫn hợp lệ trước khi ghi lựa chọn mới.
            customerBookingService.calculateSummary(
                    selection,
                    (String) session.getAttribute("seatHoldToken"),
                    Map.of(),
                    Map.of(),
                    null
            );
        } catch (IllegalArgumentException ex) {
            // Biến validation nghiệp vụ thành HTTP 400 vì đây là POST có trạng thái session không hợp lệ.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        try {
            // Parse các field combo_{id}, chặn số lượng ngoài 0..10 và kiểm tra lại catalog DB.
            LinkedHashMap<Long, Integer> combos = customerBookingService.validateComboQuantities(params);

            // Làm tương tự cho các field food_{id}.
            LinkedHashMap<Long, Integer> foodItems = customerBookingService.validateFoodItemQuantities(params);

            // Chỉ lưu ID → số lượng; tuyệt đối không lưu giá do browser gửi.
            session.setAttribute("selectedCombos", combos);
            session.setAttribute("selectedFoodItems", foodItems);

            // Đồ ăn đổi nên voucher phải được đánh giá lại theo tổng mới.
            clearSelectedVoucher(session);
            return "redirect:/booking/summary";
        } catch (IllegalArgumentException ex) {
            // Catalog thay đổi trong lúc người dùng chọn sẽ được báo và cho chọn lại.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/combos";
        }
    }

    @GetMapping("/summary")
    /**
     * Màn xác nhận booking: tính lại toàn bộ tiền vé, combo, món lẻ và voucher.
     * Nếu voucher đang chọn vừa hết hiệu lực, controller bỏ voucher rồi tải lại summary
     * thay vì làm người dùng mất ghế đang giữ.
     */
    public String summary(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        // Summary cần selection do bước chọn suất tạo.
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";

        // Voucher ví và việc tạo booking đều gắn tài khoản nên bắt buộc đăng nhập từ bước này.
        Account account = requireLoggedInAccount(session, redirectAttributes);
        if (account == null) return "redirect:/login";

        // Đọc ID voucher đã chọn ở lần tải trước; null nghĩa là không dùng voucher.
        Long selectedVoucherId = selectedVoucherId(session);
        try {
            // Tính lại toàn bộ tiền từ hold, catalog và voucher hiện hành; không dùng tổng do frontend tính.
            CustomerBookingService.BookingSummary summary = customerBookingService.calculateSummaryWithWalletVoucher(
                    account.getAccountID(), selection,
                    (String) session.getAttribute("seatHoldToken"), selectedCombos(session),
                    selectedFoodItems(session),
                    selectedVoucherId);

            // Truyền tài khoản và kết quả tổng hợp cho template.
            model.addAttribute("user", account);
            model.addAttribute("summary", summary);

            // Đọc tất cả voucher trong ví và kèm eligible/reason để UI khóa lựa chọn không hợp lệ.
            model.addAttribute("voucherOptions", customerBookingService.getWalletVoucherOptions(
                    account.getAccountID(), selection, (String) session.getAttribute("seatHoldToken"),
                    selectedCombos(session), selectedFoodItems(session)));

            // Dùng để đánh dấu radio voucher đang chọn.
            model.addAttribute("selectedVoucherId", selectedVoucherId);

            // Render templates/booking-summary.html.
            return "booking-summary";
        } catch (IllegalArgumentException ex) {
            // Nếu lỗi phát sinh từ voucher đang chọn, bỏ riêng voucher nhưng giữ hold/ghế/đồ ăn.
            if (selectedVoucherId != null) {
                clearSelectedVoucher(session);
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/booking/summary";
            }

            // Không có voucher mà vẫn lỗi thường là hold đã hết hoặc dữ liệu ghế không còn hợp lệ.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    @PostMapping("/voucher")
    /** Áp dụng hoặc gỡ voucher trong ví, sau đó buộc summary tính lại từ dữ liệu hiện hành. */
    public String applyVoucher(@RequestParam(value = "voucherId", required = false) String voucherIdValue,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        // Voucher luôn áp dụng trên một booking selection cụ thể.
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";

        // Chỉ chủ tài khoản mới được dùng voucher đã lưu trong ví của mình.
        Account account = requireLoggedInAccount(session, redirectAttributes);
        if (account == null) return "redirect:/login";
        try {
            // Chuỗi rỗng được đổi thành null; chuỗi không phải số phát sinh lỗi validation.
            Long voucherId = parseVoucherId(voucherIdValue);

            // Radio "Không dùng voucher" gửi chuỗi rỗng nên voucherId bằng null.
            if (voucherId == null) {
                // Ghi nhớ có voucher cũ hay không để chọn thông báo chính xác.
                boolean hadVoucher = selectedVoucherId(session) != null;

                // Xóa cả ID và code voucher khỏi session.
                clearSelectedVoucher(session);

                // Flash message chỉ hiển thị một lần sau redirect.
                redirectAttributes.addFlashAttribute("success",
                        hadVoucher ? "Đã bỏ voucher khỏi đơn." : "Đơn hàng sẽ không dùng voucher.");
                return "redirect:/booking/summary";
            }

            // strict validation: voucher phải thuộc ví và thỏa mọi điều kiện của đơn hiện tại.
            CustomerBookingService.BookingSummary summary = customerBookingService.calculateSummaryWithWalletVoucher(
                    account.getAccountID(), selection, (String) session.getAttribute("seatHoldToken"),
                    selectedCombos(session), selectedFoodItems(session), voucherId);

            // Lưu ID nội bộ cho những lần tính summary sau.
            session.setAttribute(SELECTED_VOUCHER_ID_SESSION_KEY, voucherId);

            // Lưu code chỉ để tương thích/hiển thị; service vẫn tra cứu bằng ID.
            session.setAttribute("voucherCode", summary.voucherCode());
            redirectAttributes.addFlashAttribute("success", "Áp dụng voucher thành công.");
        } catch (IllegalArgumentException ex) {
            // Không để voucher lỗi tiếp tục bám session và làm summary lỗi lặp.
            clearSelectedVoucher(session);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        // Redirect bắt buộc summary tính lại toàn bộ model theo lựa chọn mới.
        return "redirect:/booking/summary";
    }

    @PostMapping("/confirm")
    /**
     * Chốt snapshot đơn PENDING trước khi sang thanh toán.
     * Service sao chép tên/đơn giá từng vé, combo và món lẻ vào các bảng booking để
     * thay đổi catalog về sau không làm sai hóa đơn đã tạo.
     */
    public String confirmBooking(HttpSession session, RedirectAttributes redirectAttributes) {
        // Xác nhận vẫn phải bắt đầu từ selection server đã tạo.
        BookingSelection selection = requireSelection(session, redirectAttributes);
        if (selection == null) return "redirect:/movies";

        // Account từ session là chủ sở hữu booking sắp được tạo.
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để thanh toán.");
            return "redirect:/login";
        }
        try {
            // Service kiểm tra lại hold, giá, catalog, voucher rồi lưu booking và các dòng snapshot trong transaction.
            var booking = customerBookingService.createPendingBookingWithWalletVoucher(account.getAccountID(), selection,
                    (String) session.getAttribute("seatHoldToken"), selectedCombos(session),
                    selectedFoodItems(session),
                    selectedVoucherId(session));

            // Lưu ID để các phần khác của session có thể tham chiếu đơn vừa tạo.
            session.setAttribute("bookingId", booking.getId());

            // Chuyển ID qua query parameter; PaymentController vẫn kiểm tra owner bằng accountId.
            return "redirect:/payment?bookingId=" + booking.getId();
        } catch (IllegalArgumentException ex) {
            // Hold/catalog/voucher không hợp lệ thì không có booking nào được tạo.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/seats";
        }
    }

    private BookingSelection requireSelection(HttpSession session, RedirectAttributes redirectAttributes) {
        // Đọc đúng key dùng chung của cả booking flow.
        BookingSelection selection = (BookingSelection) session.getAttribute(BOOKING_SELECTION_SESSION_KEY);

        // Chuẩn bị thông báo để method gọi chỉ cần chọn redirect.
        if (selection == null) redirectAttributes.addFlashAttribute("error", "Vui lòng chọn suất chiếu trước.");
        return selection;
    }

    private Account requireLoggedInAccount(HttpSession session, RedirectAttributes redirectAttributes) {
        // Không nhận accountId từ request để tránh mạo danh tài khoản khác.
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tiếp tục đặt vé.");
        }
        return account;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Long, Integer> selectedCombos(HttpSession session) {
        // Session lưu dưới kiểu Object nên cần kiểm tra runtime type trước khi cast.
        Object value = session.getAttribute("selectedCombos");

        // LinkedHashMap giữ thứ tự người dùng chọn; session chưa có dữ liệu trả map rỗng an toàn.
        return value instanceof LinkedHashMap<?, ?> ? (LinkedHashMap<Long, Integer>) value : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Long, Integer> selectedFoodItems(HttpSession session) {
        // Đọc Object vì HttpSession không giữ generic type.
        Object value = session.getAttribute("selectedFoodItems");

        // Chỉ cast đúng LinkedHashMap; session cũ/không có dữ liệu được xem như lựa chọn rỗng.
        return value instanceof LinkedHashMap<?, ?>
                ? (LinkedHashMap<Long, Integer>) value : new LinkedHashMap<>();
    }

    private Long selectedVoucherId(HttpSession session) {
        // Đọc giá trị có thể đến từ session mới hoặc session cũ với kiểu khác nhau.
        Object value = session.getAttribute(SELECTED_VOUCHER_ID_SESSION_KEY);

        // Nhánh thông thường: controller hiện tại lưu trực tiếp Long.
        if (value instanceof Long id) return id;

        // Tương thích các Number implementation khác.
        if (value instanceof Number number) return number.longValue();

        // Tương thích session cũ từng lưu ID dưới dạng String.
        if (value instanceof String text) {
            try {
                return parseVoucherId(text);
            } catch (IllegalArgumentException ex) {
                // Dữ liệu session sai định dạng được dọn để không gây lỗi ở mọi request sau.
                clearSelectedVoucher(session);
            }
        }

        // Không có hoặc không nhận diện được nghĩa là chưa chọn voucher.
        return null;
    }

    private Long parseVoucherId(String value) {
        // Radio "không dùng voucher" gửi null/rỗng.
        if (value == null || value.isBlank()) return null;
        try {
            // trim loại khoảng trắng trước khi parse khóa chính.
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            // Không để NumberFormatException kỹ thuật lộ trực tiếp ra giao diện.
            throw new IllegalArgumentException("Voucher được chọn không hợp lệ.");
        }
    }

    private void clearSelectedVoucher(HttpSession session) {
        // Xóa khóa chính dùng cho truy vấn.
        session.removeAttribute(SELECTED_VOUCHER_ID_SESSION_KEY);

        // Xóa code tương thích để không hiển thị voucher cũ.
        session.removeAttribute("voucherCode");
    }

    @PostMapping("/release-hold")
    @ResponseBody
    public Map<String, Object> releaseHold(HttpSession session) {
        // sendBeacon không gửi token; server lấy token đúng của phiên từ session cookie.
        String token = (String) session.getAttribute("seatHoldToken");

        // Chỉ xóa khi token tồn tại.
        if (token != null && !token.isBlank()) {
            // Xóa booking_tickets chưa BOOKED của token.
            seatHoldingService.releaseHold(token);

            // Dọn bộ đếm và token khỏi session sau khi DB đã được xử lý.
            session.removeAttribute("seatHoldToken");
            session.removeAttribute("seatHoldExpiresAt");

            // Không xóa BOOKING_SELECTION_SESSION_KEY vì beacon có thể chạy đồng thời với submit chuyển trang.
        }

        // Spring serialize map này thành {"success":true} cho request beacon/AJAX.
        return Map.of("success", true);
    }

    private void clearBookingSteps(HttpSession session) {
        // Xóa token và hạn giữ ghế của suất cũ.
        session.removeAttribute("seatHoldToken");
        session.removeAttribute("seatHoldExpiresAt");

        // Xóa catalog selection phụ thuộc vào ghế/suất cũ.
        session.removeAttribute("selectedCombos");
        session.removeAttribute("selectedFoodItems");

        // Xóa voucher vì điều kiện áp dụng có thể khác với suất mới.
        clearSelectedVoucher(session);

        // Xóa booking PENDING cũ khỏi session; bản ghi DB nếu có có vòng đời riêng.
        session.removeAttribute("bookingId");
    }
}
