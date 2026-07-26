package com.group3.cinema.service;

/*
 * Nghiệp vụ đánh giá phim của khách hàng và kiểm duyệt đánh giá của quản trị viên.
 * Khách chỉ được đánh giá phim đã thanh toán và có suất chiếu thực sự kết thúc/đã qua.
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.MovieReview;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.MovieReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MovieReviewService {

    // APPROVED là trạng thái duy nhất được công khai và được tính vào điểm trung bình.
    private static final MovieReview.ModerationStatus VISIBLE_STATUS = MovieReview.ModerationStatus.APPROVED;

    // REJECTED được dùng như trạng thái ẩn mềm, không xóa bản ghi review.
    private static final MovieReview.ModerationStatus HIDDEN_STATUS = MovieReview.ModerationStatus.REJECTED;

    private final MovieReviewRepository movieReviewRepository;
    private final MovieRepository movieRepository;
    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;

    public MovieReviewService(MovieReviewRepository movieReviewRepository,
                              MovieRepository movieRepository,
                              AccountRepository accountRepository,
                              BookingRepository bookingRepository) {
        // Lưu các repository Spring truyền vào để service thực hiện nghiệp vụ trong cùng transaction.
        this.movieReviewRepository = movieReviewRepository;
        this.movieRepository = movieRepository;
        this.accountRepository = accountRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<MovieReview> getApprovedReviews(int movieId) {
        // Derived query lọc đúng phim + APPROVED và sắp xếp review mới nhất trước.
        return movieReviewRepository.findByMovieIdAndModerationStatusOrderByReviewDateDesc(movieId, VISIBLE_STATUS);
    }

    public Page<MovieReview> getApprovedReviews(int movieId, Integer ratingScore, Pageable pageable) {
        // Overload không lọc ngày chuyển tiếp về method đầy đủ với hai đầu ngày bằng null.
        return getApprovedReviews(movieId, ratingScore, null, null, pageable);
    }

    public Page<MovieReview> getApprovedReviews(int movieId,
                                                Integer ratingScore,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                Pageable pageable) {
        // Ngày bắt đầu được mở rộng thành 00:00 để bao gồm toàn bộ ngày đó.
        LocalDateTime startDateTime = startDate == null ? null : startDate.atStartOfDay();

        // Ngày kết thúc được mở rộng tới 23:59:59 để không bỏ review trong ngày cuối.
        LocalDateTime endDateTime = endDate == null ? null : endDate.atTime(23, 59, 59);

        // Chỉ truyền rating hợp lệ 1..5; giá trị khác được coi là không lọc sao.
        if (ratingScore != null && ratingScore >= 1 && ratingScore <= 5) {
            return movieReviewRepository.searchVisibleReviews(movieId, VISIBLE_STATUS, ratingScore, startDateTime, endDateTime, pageable);
        }

        // ratingScore = null làm điều kiện (:ratingScore IS NULL OR ...) trong JPQL luôn đúng.
        return movieReviewRepository.searchVisibleReviews(movieId, VISIBLE_STATUS, null, startDateTime, endDateTime, pageable);
    }

    public double getAverageRating(int movieId) {
        // Repository dùng AVG + COALESCE nên phim chưa có review trả 0 thay vì null.
        return movieReviewRepository.averageRating(movieId, VISIBLE_STATUS);
    }

    public long getApprovedReviewCount(int movieId) {
        // Chỉ đếm review đang công khai.
        return movieReviewRepository.reviewCount(movieId, VISIBLE_STATUS);
    }

    public List<MovieReview> getAllReviewsForAdmin() {
        // Admin được xem cả APPROVED và REJECTED, mới nhất trước.
        return movieReviewRepository.findAllByOrderByReviewDateDesc();
    }

    public List<MovieReview> searchReviewsForAdmin(String keyword,
                                                   String status,
                                                   LocalDate startDate,
                                                   LocalDate endDate) {
        // Chuẩn hóa keyword để tìm kiếm không phân biệt dấu và hoa/thường.
        String normalizedKeyword = normalize(keyword);

        // null status mặc định ALL; trim và uppercase để nhận URL viết khác kiểu.
        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);

        // Đọc danh sách theo thứ tự mới nhất rồi áp dụng lần lượt ba nhóm predicate.
        return movieReviewRepository.findAllByOrderByReviewDateDesc().stream()
                // Lọc APPROVED/REJECTED hoặc nhận tất cả.
                .filter(review -> matchesStatus(review, normalizedStatus))
                // Lọc ngày review theo khoảng bao gồm hai đầu.
                .filter(review -> matchesDateRange(review, startDate, endDate))
                // Tìm trên phim, khách hàng, email và nội dung comment.
                .filter(review -> matchesKeyword(review, normalizedKeyword))
                .toList();
    }

    public long getTotalReviewCount() {
        // JpaRepository.count trả tổng cả review hiện và ẩn.
        return movieReviewRepository.count();
    }

    public long getVisibleReviewCount() {
        // Derived count chỉ đếm APPROVED.
        return movieReviewRepository.countByModerationStatus(VISIBLE_STATUS);
    }

    public Optional<MovieReview> getUserReview(int movieId, Integer accountId) {
        // Khách chưa đăng nhập chắc chắn không có review cá nhân để nạp vào form.
        if (accountId == null) {
            return Optional.empty();
        }

        // Cặp movieId + accountId xác định review duy nhất của người dùng cho phim.
        return movieReviewRepository.findByMovieIdAndAccountAccountID(movieId, accountId);
    }

    public boolean canReviewMovie(Integer accountId, int movieId) {
        // accountId null trả false ngay nhờ short-circuit, không gọi database.
        return accountId != null && bookingRepository.existsWatchedMovie(
                // Tài khoản cần kiểm tra.
                accountId,
                // Phim đang mở trang chi tiết.
                movieId,
                // Native query so sánh chuỗi trạng thái PAID.
                Booking.Status.PAID.name(),
                // Ngày hiện tại để phân biệt suất ngày trước/ngày hôm nay.
                LocalDate.now(),
                // Giờ hiện tại để suất hôm nay chỉ hợp lệ khi showTime đã qua.
                LocalTime.now()
        );
    }

    @Transactional
    public void submitReview(int movieId, int accountId, int ratingScore, String comment) {
        // Rating ngoài 1..5 bị từ chối kể cả khi frontend select không cho chọn.
        if (ratingScore < 1 || ratingScore > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5 sao.");
        }

        // Trim comment, đổi rỗng thành null và chặn quá 1000 ký tự.
        String cleanComment = normalizeComment(comment);

        // Chỉ phim active mới được nhận review.
        Movie movie = movieRepository.findByIdAndActiveTrue(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phim."));

        // Account phải còn tồn tại trong database, không chỉ có ID trong session cũ.
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

        // Lấy booking PAID gần nhất chứng minh user đã xem đúng phim.
        Booking watchedBooking = bookingRepository.findWatchedBookings(
                        accountId,
                        movieId,
                        Booking.Status.PAID.name(),
                        LocalDate.now(),
                        LocalTime.now()
                ).stream()
                // Query đã order mới nhất trước nên phần tử đầu là bằng chứng gần nhất.
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ban chi co the danh gia sau khi da xem phim."));

        // Có review cũ thì cập nhật; chưa có thì tạo entity mới.
        MovieReview review = movieReviewRepository.findByMovieIdAndAccountAccountID(movieId, accountId)
                .orElseGet(MovieReview::new);

        // Gắn đầy đủ quan hệ và snapshot booking chứng minh quyền đánh giá.
        review.setMovie(movie);
        review.setAccount(account);
        review.setBookingId(watchedBooking.getId());

        // Ghi nội dung mới và thời điểm gửi/cập nhật hiện tại.
        review.setRatingScore(ratingScore);
        review.setComment(cleanComment);
        review.setReviewDate(LocalDateTime.now());

        // Nội dung mới được đưa về visible; xóa metadata moderation cũ vì nó không còn áp dụng.
        review.setModerationStatus(VISIBLE_STATUS);
        review.setModeratedBy(null);
        review.setModeratedAt(null);

        // INSERT nếu entity mới, UPDATE nếu đã có ID.
        movieReviewRepository.save(review);
    }

    @Transactional
    public void setReviewVisible(Long reviewId, int adminAccountId, boolean visible) {
        // Tìm review mục tiêu hoặc báo lỗi nghiệp vụ.
        MovieReview review = movieReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá."));

        // Tìm Account admin để lưu dấu vết ai đã thao tác.
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản quản trị."));

        // true ánh xạ APPROVED, false ánh xạ REJECTED; không xóa vật lý.
        review.setModerationStatus(visible ? VISIBLE_STATUS : HIDDEN_STATUS);

        // Ghi người và thời điểm kiểm duyệt phục vụ audit.
        review.setModeratedBy(admin);
        review.setModeratedAt(LocalDateTime.now());

        // Persist thay đổi trong transaction.
        movieReviewRepository.save(review);
    }

    private String normalizeComment(String comment) {
        // Comment không bắt buộc; null/rỗng được lưu null.
        if (comment == null || comment.isBlank()) {
            return null;
        }

        // Loại khoảng trắng thừa đầu/cuối trước khi đo chiều dài và lưu.
        String trimmed = comment.trim();

        // Đồng bộ với maxlength=1000 ở frontend nhưng vẫn bảo vệ request sửa tay.
        if (trimmed.length() > 1000) {
            throw new IllegalArgumentException("Bình luận không được vượt quá 1000 ký tự.");
        }

        // Trả nội dung đã làm sạch.
        return trimmed;
    }

    private boolean matchesStatus(MovieReview review, String status) {
        // APPROVED được coi là visible; các trạng thái khác là hidden trong màn hiện tại.
        boolean visible = review.getModerationStatus() == VISIBLE_STATUS;

        // Hỗ trợ cả nhãn UI VISIBLE và tên enum APPROVED.
        if ("VISIBLE".equals(status) || "APPROVED".equals(status)) {
            return visible;
        }

        // Hỗ trợ cả nhãn UI HIDDEN và tên enum REJECTED.
        if ("HIDDEN".equals(status) || "REJECTED".equals(status)) {
            return !visible;
        }

        // ALL hoặc status lạ không loại review nào.
        return true;
    }

    private boolean matchesDateRange(MovieReview review, LocalDate startDate, LocalDate endDate) {
        // Chỉ phần ngày của reviewDate được so sánh với input type=date.
        LocalDate reviewDate = review.getReviewDate().toLocalDate();

        // Mỗi đầu null nghĩa là không giới hạn; !isBefore/!isAfter giúp bao gồm ngày biên.
        return (startDate == null || !reviewDate.isBefore(startDate))
                && (endDate == null || !reviewDate.isAfter(endDate));
    }

    private boolean matchesKeyword(MovieReview review, String normalizedKeyword) {
        // Không nhập keyword nghĩa là mọi review đều khớp.
        if (normalizedKeyword == null) {
            return true;
        }

        // OR trên bốn trường phục vụ tìm theo phim, khách hoặc nội dung.
        return containsNormalized(review.getMovie().getTitle(), normalizedKeyword)
                || containsNormalized(review.getAccount().getName(), normalizedKeyword)
                || containsNormalized(review.getAccount().getEmail(), normalizedKeyword)
                || containsNormalized(review.getComment(), normalizedKeyword);
    }

    private boolean containsNormalized(String value, String normalizedKeyword) {
        // Chuẩn hóa giá trị DB theo cùng quy tắc với keyword.
        String normalizedValue = normalize(value);

        // null không khớp; contains cho phép tìm một phần chuỗi.
        return normalizedValue != null && normalizedValue.contains(normalizedKeyword);
    }

    private String normalize(String value) {
        // null/rỗng được biểu diễn bằng null để caller hiểu là không có điều kiện.
        if (value == null || value.isBlank()) {
            return null;
        }

        // NFD tách dấu khỏi ký tự Latin, regex xóa các dấu kết hợp.
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Xử lý riêng Đ/đ rồi lowercase ổn định, không phụ thuộc locale máy chủ.
        return normalized
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT);
    }
}
