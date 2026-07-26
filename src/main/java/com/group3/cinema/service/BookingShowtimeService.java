/**
 * Service xử lý logic tra cứu suất chiếu và kiểm tra số ghế trống phục vụ Khách hàng đặt vé online (`BookingShowtimeService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `BookingController` khi khách xem lịch chiếu theo phim, chọn ngày và kiểm tra tình trạng chỗ trống.
 * - Gọi đến các Repository:
 *   + `MovieRepository`: Kiểm tra trạng thái phim có thể bán vé (`getBookableMovie`).
 *   + `ShowtimeRepository`: Tra cứu lịch chiếu khả dụng cho khách hàng (`searchShowtimesForCustomer`).
 *   + `RoomRepository`: Kiểm tra phòng chiếu có đang hoạt động không (`findActiveRoom`).
 *   + `SeatRepository`: Lấy ma trận sơ đồ ghế của phòng.
 *   + `SeatTypeRepository`: Lấy cấu hình sức chứa của các loại ghế.
 *   + `BookingTicketRepository`: Tính toán tổng số sức chứa đã bị giữ chỗ/đã thanh toán để suy ra ghế còn trống.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.dto.BookingShowtimeDateView;
import com.group3.cinema.dto.BookingShowtimeView;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.SeatTypeRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookingShowtimeService {

    // Dùng khi Movie chưa có duration hợp lệ để vẫn tính được giờ kết thúc.
    private static final int DEFAULT_DURATION_MINUTES = 120;

    // Chuỗi phải khớp trạng thái phòng đang được phép bán vé trong database.
    private static final String ACTIVE_ROOM_STATUS = "Hoạt động";

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final BookingTicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BookingShowtimeService(ShowtimeRepository showtimeRepository,
                                  MovieRepository movieRepository,
                                  RoomRepository roomRepository,
                                  BookingTicketRepository ticketRepository,
                                  SeatRepository seatRepository,
                                  SeatTypeRepository seatTypeRepository,
                                  JdbcTemplate jdbcTemplate) {
        // Lưu toàn bộ repository/helper được Spring inject để các method nghiệp vụ sử dụng.
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.seatRepository = seatRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lấy thông tin phim hợp lệ có thể mở bán vé (`NOW_SHOWING` hoặc `SPECIAL_SCREENING`).
     * 
     * @param movieId ID bộ phim.
     * @return Đối tượng Movie.
     */
    public Movie getBookableMovie(int movieId) {
        // Query chỉ lấy phim active theo khóa chính.
        return movieRepository.findByIdAndActiveTrue(movieId)
                // Phim sắp chiếu/dừng chiếu không được đi tiếp vào booking.
                .filter(movie -> movie.getStatus() == Movie.MovieStatus.NOW_SHOWING
                        || movie.getStatus() == Movie.MovieStatus.SPECIAL_SCREENING)
                // Optional rỗng được đổi thành lỗi nghiệp vụ có thể hiển thị cho người dùng.
                .orElseThrow(() -> new IllegalArgumentException("Phim không tồn tại hoặc hiện chưa mở bán vé."));
    }

    /**
     * Lấy danh sách các suất chiếu khả dụng của một bộ phim theo ngày chỉ định.
     */
    public List<BookingShowtimeView> getAvailableShowtimes(int movieId, LocalDate date) {
        // Ngày null hoặc quá khứ không thể bán vé.
        if (date == null || date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Vui lòng điền ngày chiếu hợp lệ.");
        }

        // Xác minh phim trước khi query lịch.
        Movie movie = getBookableMovie(movieId);

        // Chụp mốc now một lần để mọi suất trong stream so với cùng thời điểm.
        LocalDateTime now = LocalDateTime.now();

        // Query các suất active đúng phim và đúng ngày.
        return showtimeRepository.searchShowtimesForCustomer(movieId, null, date, date).stream()
                // Loại suất đã bắt đầu hoặc đúng bằng thời điểm hiện tại.
                .filter(showtime -> LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime()).isAfter(now))
                // Chuyển entity thành DTO và tính số ghế còn lại.
                .map(showtime -> toView(showtime, movie))
                // toView trả null khi phòng lỗi; suất hết ghế cũng bị loại.
                .filter(view -> view != null && view.availableSeatCount() > 0)
                .toList();
    }

    /**
     * Lấy lịch chiếu tổng hợp theo chuỗi các ngày (trong 30 ngày tiếp theo) để hiển thị tab chọn ngày trên UI.
     */
    public List<BookingShowtimeDateView> getAvailableShowtimeSchedule(int movieId) {
        // Chặn phim chưa mở bán ngay từ đầu.
        Movie movie = getBookableMovie(movieId);

        // Khoảng lịch bắt đầu hôm nay.
        LocalDate today = LocalDate.now();

        // Chỉ tải tối đa 30 ngày để giới hạn dữ liệu và phạm vi đặt vé.
        LocalDate maxDate = today.plusDays(30);

        // Dùng cùng một mốc giờ để lọc tất cả suất.
        LocalDateTime now = LocalDateTime.now();

        // LinkedHashMap giữ thứ tự ngày mà query đã trả về.
        Map<LocalDate, List<BookingShowtimeView>> grouped = new LinkedHashMap<>();

        // Query suất active/công khai trong khoảng today..maxDate.
        showtimeRepository.searchShowtimesForCustomer(movieId, null, today, maxDate).stream()
                // Loại suất đã bắt đầu.
                .filter(showtime -> LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime()).isAfter(now))
                // Đổi sang view model chứa phòng, format, giờ kết thúc và ghế trống.
                .map(showtime -> toView(showtime, movie))
                // Loại phòng không hoạt động và suất không còn chỗ.
                .filter(view -> view != null && view.availableSeatCount() > 0)
                // Tạo list cho ngày chưa có rồi thêm suất vào đúng nhóm.
                .forEach(view -> grouped.computeIfAbsent(view.showDate(), ignored -> new java.util.ArrayList<>()).add(view));

        // Đổi mỗi entry ngày → danh sách suất thành DTO mà Thymeleaf có thể render.
        return grouped.entrySet().stream()
                .map(entry -> new BookingShowtimeDateView(entry.getKey(), dayOfWeekLabel(entry.getKey()), entry.getValue()))
                .toList();
    }

    /** Lấy tên rạp chiếu phim cấu hình trong CSDL (`booking_settings`). */
    public String getCinemaName() {
        // JdbcTemplate dùng cho bảng cấu hình đơn giản chưa ánh xạ thành Entity.
        List<String> values = jdbcTemplate.query(
                "SELECT setting_value FROM booking_settings WHERE setting_key = 'cinema_name'",
                // Mỗi row chỉ cần cột setting_value.
                (rs, rowNum) -> rs.getString("setting_value")
        );

        // Không có cấu hình trả chuỗi rỗng thay vì lỗi IndexOutOfBoundsException.
        return values.isEmpty() ? "" : values.get(0);
    }

    /**
     * Xác thực suất chiếu khách hàng chọn và khởi tạo đối tượng `BookingSelection` chứa dữ liệu phiên chọn.
     */
    public BookingSelection validateAndCreateSelection(long showtimeId, int movieId, LocalDate date) {
        // Ba giá trị bắt buộc phải hợp lệ về mặt hình thức trước khi query database.
        if (showtimeId <= 0 || movieId <= 0 || date == null) {
            throw new IllegalArgumentException("Thông tin phim, ngày hoặc suất chiếu chưa đầy đủ.");
        }

        // Xác minh phim đang được mở bán.
        Movie movie = getBookableMovie(movieId);

        // Đọc Showtime thật theo ID thay vì tin hidden input.
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Suất chiếu không tồn tại."));

        // Kiểm tra Showtime thuộc đúng movieId và showDate mà form gửi.
        if (showtime.getMovie() == null || showtime.getMovie().getId() != movieId
                || !date.equals(showtime.getShowDate())) {
            throw new IllegalArgumentException("Suất chiếu không khớp với phim và ngày đã chọn.");
        }

        // Chặn trường hợp suất bắt đầu trong khoảng thời gian từ lúc render đến lúc bấm chọn.
        if (!LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime()).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Suất chiếu đã bắt đầu. Vui lòng chọn suất khác.");
        }

        // Ánh xạ tên phòng trong Showtime sang Room và yêu cầu trạng thái hoạt động.
        Room room = findActiveRoom(showtime.getRoom());

        // Tính lại sức chứa ngay lúc xác nhận để chặn suất vừa hết chỗ.
        if (availableSeats(showtime.getId(), room) <= 0) {
            throw new IllegalArgumentException("Suất chiếu hiện đã hết chỗ hoặc chưa có sơ đồ ghế.");
        }

        // Tạo DTO bất biến, chỉ chứa dữ liệu các bước booking sau thực sự cần.
        return new BookingSelection(showtime.getId(), movieId, room.getId(), movie.getTitle(),
                room.getRoomName(), showtime.getShowDate(), showtime.getShowTime(),
                showtime.getShowTime().plusMinutes(resolveDuration(movie)), resolveFormat(room));
    }

    private BookingShowtimeView toView(Showtime showtime, Movie movie) {
        try {
            // Tìm và xác minh phòng trước khi dựng view.
            Room room = findActiveRoom(showtime.getRoom());

            // DTO tránh đưa entity graph trực tiếp ra template.
            return new BookingShowtimeView(showtime.getId(), showtime.getShowDate(), showtime.getShowTime(),
                    showtime.getShowTime().plusMinutes(resolveDuration(movie)), room.getRoomName(),
                    resolveFormat(room), availableSeats(showtime.getId(), room));
        } catch (IllegalArgumentException ignored) {
            // Một suất có cấu hình phòng lỗi bị loại khỏi lịch thay vì làm hỏng toàn bộ trang.
            return null;
        }
    }

    private Room findActiveRoom(String roomName) {
        // Tên phòng trong Showtime được tìm không phân biệt hoa/thường.
        Room room = roomRepository.findFirstByRoomNameIgnoreCase(roomName)
                .orElseThrow(() -> new IllegalArgumentException("Phòng chiếu không tồn tại."));

        // Chỉ trạng thái "Hoạt động" mới được bán vé.
        if (!ACTIVE_ROOM_STATUS.equalsIgnoreCase(room.getStatus())) {
            throw new IllegalArgumentException("Phòng chiếu đang tạm ngưng hoạt động.");
        }

        // Trả Room đã xác minh để caller lấy ID/type/layout.
        return room;
    }

    private int resolveDuration(Movie movie) {
        // Duration thiếu/không dương dùng mặc định 120 phút; ngược lại dùng dữ liệu phim.
        return movie.getDuration() == null || movie.getDuration() <= 0
                ? DEFAULT_DURATION_MINUTES : movie.getDuration();
    }

    private String resolveFormat(Room room) {
        // Room type thiếu dùng 2D để giao diện luôn có format hiển thị.
        return room.getRoomType() == null || room.getRoomType().isBlank() ? "2D" : room.getRoomType();
    }

    private String dayOfWeekLabel(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }

    /**
     * Tính toán số sức chứa ghế còn trống cho suất chiếu chỉ định.
     */
    private int availableSeats(Long showtimeId, Room room) {
        // Tạo map code loại ghế → metadata để tra capacity/active/sellable nhanh.
        Map<String, SeatType> seatTypes = seatTypeRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(type -> normalizeType(type.getCode()), Function.identity(), (first, ignored) -> first));

        // Đọc toàn bộ sơ đồ ghế của phòng theo tọa độ.
        List<Seat> seats = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(room.getId());

        // Map seatId → Seat giúp quy đổi BookingTicket về sức chứa vật lý.
        Map<Long, Seat> seatById = seats.stream().collect(Collectors.toMap(Seat::getId, Function.identity()));

        // Tổng sức chứa chỉ cộng các ghế có loại active, sellable và capacity > 0.
        int totalCapacity = seats.stream()
                .filter(seat -> isSellableSeat(seat, seatTypes))
                // Ghế đôi cộng capacity=2 thay vì chỉ đếm một record Seat.
                .mapToInt(seat -> seatTypes.get(normalizeType(seat.getSeatType())).getCapacity())
                .sum();

        // Đọc các trạng thái giữ/đặt của đúng suất chiếu.
        int occupiedCapacity = ticketRepository.findByShowtimeId(showtimeId).stream()
                // BOOKED luôn chiếm chỗ; HOLDING chỉ chiếm khi chưa hết hạn.
                .filter(ticket -> ticket.getStatus() == com.group3.cinema.entity.BookingTicket.Status.BOOKED
                        || (ticket.getHoldExpiresAt() != null && ticket.getHoldExpiresAt().isAfter(LocalDateTime.now())))
                // Từ seatId của ticket lấy Seat vật lý.
                .map(ticket -> seatById.get(ticket.getSeatId()))
                // Bỏ ticket mồ côi hoặc ghế hiện không bán.
                .filter(seat -> seat != null && isSellableSeat(seat, seatTypes))
                // Cộng theo capacity, không theo số record.
                .mapToInt(seat -> seatTypes.get(normalizeType(seat.getSeatType())).getCapacity())
                .sum();

        // Math.max bảo vệ dữ liệu lệch khiến occupied lớn hơn total.
        return Math.max(0, totalCapacity - occupiedCapacity);
    }

    private boolean isSellableSeat(Seat seat, Map<String, SeatType> seatTypes) {
        // Chuẩn hóa code lưu trên Seat để khớp key trong map.
        String type = normalizeType(seat.getSeatType());

        // "skip" là ô trống trong layout, không phải ghế bán.
        if ("skip".equals(type)) {
            return false;
        }

        // Metadata có thể không tồn tại nếu dữ liệu ghế dùng code đã bị xóa.
        SeatType meta = seatTypes.get(type);

        // Chỉ ghế có loại tồn tại, active, sellable và capacity dương mới được tính.
        return meta != null && meta.isActive() && meta.isSellable() && meta.getCapacity() > 0;
    }

    private String normalizeType(String type) {
        // Code rỗng tương thích dữ liệu cũ bằng cách mặc định về ghế standard "std".
        return type == null || type.isBlank() ? "std" : type.trim().toLowerCase();
    }
}

