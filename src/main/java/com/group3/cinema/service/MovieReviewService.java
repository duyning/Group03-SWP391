/**
 * Service quản lý Đánh giá & Bình luận phim của Khách hàng và Kiểm duyệt nội dung từ Quản trị viên (`MovieReviewService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CustomerMovieReviewController` (Gửi review/xem review) và `AdminMovieReviewController` (Ẩn/Hiện bình luận vi phạm).
 * - Tương tác với:
 *   + `MovieReviewRepository`: Truy vấn đánh giá hiển thị công khai (`searchVisibleReviews`), tính điểm trung bình (`averageRating`), lưu review (`save`).
 *   + `BookingRepository`: Kiểm tra điều kiện xem phim thực tế (`existsWatchedMovie`, `findWatchedBookings`) - khách chỉ được đánh giá phim sau khi đã mua vé và giờ chiếu đã trôi qua.
 *   + `MovieRepository`, `AccountRepository`: Lấy dữ liệu phim và khách hàng.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (10/07/2026)
 */
package com.group3.cinema.service;

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

    private static final MovieReview.ModerationStatus VISIBLE_STATUS = MovieReview.ModerationStatus.APPROVED;
    private static final MovieReview.ModerationStatus HIDDEN_STATUS = MovieReview.ModerationStatus.REJECTED;

    private final MovieReviewRepository movieReviewRepository;
    private final MovieRepository movieRepository;
    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;

    public MovieReviewService(MovieReviewRepository movieReviewRepository,
                              MovieRepository movieRepository,
                              AccountRepository accountRepository,
                              BookingRepository bookingRepository) {
        this.movieReviewRepository = movieReviewRepository;
        this.movieRepository = movieRepository;
        this.accountRepository = accountRepository;
        this.bookingRepository = bookingRepository;
    }

    /** Lấy danh sách đánh giá phim đã được phê duyệt (`APPROVED`) xếp mới nhất. */
    public List<MovieReview> getApprovedReviews(int movieId) {
        return movieReviewRepository.findByMovieIdAndModerationStatusOrderByReviewDateDesc(movieId, VISIBLE_STATUS);
    }

    /** Lấy danh sách đánh giá đã duyệt có phân trang. */
    public Page<MovieReview> getApprovedReviews(int movieId, Integer ratingScore, Pageable pageable) {
        return getApprovedReviews(movieId, ratingScore, null, null, pageable);
    }

    /** Lấy danh sách đánh giá đã duyệt có lọc theo mốc điểm sao và khoảng ngày gửi. */
    public Page<MovieReview> getApprovedReviews(int movieId,
                                                Integer ratingScore,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                Pageable pageable) {
        LocalDateTime startDateTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate == null ? null : endDate.atTime(23, 59, 59);

        if (ratingScore != null && ratingScore >= 1 && ratingScore <= 5) {
            return movieReviewRepository.searchVisibleReviews(movieId, VISIBLE_STATUS, ratingScore, startDateTime, endDateTime, pageable);
        }
        return movieReviewRepository.searchVisibleReviews(movieId, VISIBLE_STATUS, null, startDateTime, endDateTime, pageable);
    }

    /** Tính số điểm đánh giá trung bình (Rating Average) từ các bài review đã duyệt. */
    public double getAverageRating(int movieId) {
        return movieReviewRepository.averageRating(movieId, VISIBLE_STATUS);
    }

    /** Đếm tổng số đánh giá đã được hiển thị công khai của bộ phim. */
    public long getApprovedReviewCount(int movieId) {
        return movieReviewRepository.reviewCount(movieId, VISIBLE_STATUS);
    }

    /** Lấy toàn bộ đánh giá phục vụ quản trị Admin kiểm duyệt. */
    public List<MovieReview> getAllReviewsForAdmin() {
        return movieReviewRepository.findAllByOrderByReviewDateDesc();
    }

    /** Tìm kiếm và lọc danh sách đánh giá trong trang Admin theo từ khóa, trạng thái ẩn/hiện, khoảng thời gian. */
    public List<MovieReview> searchReviewsForAdmin(String keyword,
                                                   String status,
                                                   LocalDate startDate,
                                                   LocalDate endDate) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);

        return movieReviewRepository.findAllByOrderByReviewDateDesc().stream()
                .filter(review -> matchesStatus(review, normalizedStatus))
                .filter(review -> matchesDateRange(review, startDate, endDate))
                .filter(review -> matchesKeyword(review, normalizedKeyword))
                .toList();
    }

    /** Đếm tổng số đánh giá trong toàn bộ hệ thống. */
    public long getTotalReviewCount() {
        return movieReviewRepository.count();
    }

    /** Đếm số đánh giá đang được hiển thị công khai. */
    public long getVisibleReviewCount() {
        return movieReviewRepository.countByModerationStatus(VISIBLE_STATUS);
    }

    /** Lấy bản ghi đánh giá cá nhân của khách hàng cho bộ phim. */
    public Optional<MovieReview> getUserReview(int movieId, Integer accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return movieReviewRepository.findByMovieIdAndAccountAccountID(movieId, accountId);
    }

    /**
     * Kiểm tra xem khách hàng có đủ điều kiện viết đánh giá phim hay không (bắt buộc phải có vé đã thanh toán và thời gian suất chiếu đã kết thúc).
     */
    public boolean canReviewMovie(Integer accountId, int movieId) {
        return accountId != null && bookingRepository.existsWatchedMovie(
                accountId,
                movieId,
                Booking.Status.PAID.name(),
                LocalDate.now(),
                LocalTime.now()
        );
    }

    /**
     * Gửi hoặc Cập nhật nhận xét, số sao đánh giá cho bộ phim.
     * 
     * @param movieId ID bộ phim.
     * @param accountId ID khách hàng.
     * @param ratingScore Điểm sao từ 1 đến 5.
     * @param comment Nội dung bình luận.
     */
    @Transactional
    public void submitReview(int movieId, int accountId, int ratingScore, String comment) {
        if (ratingScore < 1 || ratingScore > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5 sao.");
        }

        String cleanComment = normalizeComment(comment);
        Movie movie = movieRepository.findByIdAndActiveTrue(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phim."));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

        Booking watchedBooking = bookingRepository.findWatchedBookings(
                        accountId,
                        movieId,
                        Booking.Status.PAID.name(),
                        LocalDate.now(),
                        LocalTime.now()
                ).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ban chi co the danh gia sau khi da xem phim."));

        MovieReview review = movieReviewRepository.findByMovieIdAndAccountAccountID(movieId, accountId)
                .orElseGet(MovieReview::new);
        review.setMovie(movie);
        review.setAccount(account);
        review.setBookingId(watchedBooking.getId());
        review.setRatingScore(ratingScore);
        review.setComment(cleanComment);
        review.setReviewDate(LocalDateTime.now());
        review.setModerationStatus(VISIBLE_STATUS);
        review.setModeratedBy(null);
        review.setModeratedAt(null);
        movieReviewRepository.save(review);
    }

    /**
     * Thay đổi trạng thái hiển thị công khai hoặc ẩn đi của bài đánh giá bởi Admin.
     * 
     * @param reviewId ID bài đánh giá.
     * @param adminAccountId ID Admin thực hiện.
     * @param visible Cờ hiển thị (true = APPROVED, false = REJECTED).
     */
    @Transactional
    public void setReviewVisible(Long reviewId, int adminAccountId, boolean visible) {
        MovieReview review = movieReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá."));
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản quản trị."));
        review.setModerationStatus(visible ? VISIBLE_STATUS : HIDDEN_STATUS);
        review.setModeratedBy(admin);
        review.setModeratedAt(LocalDateTime.now());
        movieReviewRepository.save(review);
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > 1000) {
            throw new IllegalArgumentException("Bình luận không được vượt quá 1000 ký tự.");
        }
        return trimmed;
    }

    private boolean matchesStatus(MovieReview review, String status) {
        boolean visible = review.getModerationStatus() == VISIBLE_STATUS;
        if ("VISIBLE".equals(status) || "APPROVED".equals(status)) {
            return visible;
        }
        if ("HIDDEN".equals(status) || "REJECTED".equals(status)) {
            return !visible;
        }
        return true;
    }

    private boolean matchesDateRange(MovieReview review, LocalDate startDate, LocalDate endDate) {
        LocalDate reviewDate = review.getReviewDate().toLocalDate();
        return (startDate == null || !reviewDate.isBefore(startDate))
                && (endDate == null || !reviewDate.isAfter(endDate));
    }

    private boolean matchesKeyword(MovieReview review, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        return containsNormalized(review.getMovie().getTitle(), normalizedKeyword)
                || containsNormalized(review.getAccount().getName(), normalizedKeyword)
                || containsNormalized(review.getAccount().getEmail(), normalizedKeyword)
                || containsNormalized(review.getComment(), normalizedKeyword);
    }

    private boolean containsNormalized(String value, String normalizedKeyword) {
        String normalizedValue = normalize(value);
        return normalizedValue != null && normalizedValue.contains(normalizedKeyword);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT);
    }
}

