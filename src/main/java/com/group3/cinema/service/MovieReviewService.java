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

    public List<MovieReview> getApprovedReviews(int movieId) {
        return movieReviewRepository.findByMovieIdAndModerationStatusOrderByReviewDateDesc(movieId, VISIBLE_STATUS);
    }

    public Page<MovieReview> getApprovedReviews(int movieId, Integer ratingScore, Pageable pageable) {
        return getApprovedReviews(movieId, ratingScore, null, null, pageable);
    }

    public Page<MovieReview> getApprovedReviews(int movieId,
                                                Integer ratingScore,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                Pageable pageable) {
        LocalDateTime startDateTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate == null ? null : endDate.atTime(23, 59, 59);

        // Đổi bộ lọc ngày thành khoảng thời gian bao trọn hai đầu ngày để không bỏ sót đánh giá.
        if (ratingScore != null && ratingScore >= 1 && ratingScore <= 5) {
            return movieReviewRepository.searchVisibleReviews(movieId, VISIBLE_STATUS, ratingScore, startDateTime, endDateTime, pageable);
        }
        return movieReviewRepository.searchVisibleReviews(movieId, VISIBLE_STATUS, null, startDateTime, endDateTime, pageable);
    }

    public double getAverageRating(int movieId) {
        return movieReviewRepository.averageRating(movieId, VISIBLE_STATUS);
    }

    public long getApprovedReviewCount(int movieId) {
        return movieReviewRepository.reviewCount(movieId, VISIBLE_STATUS);
    }

    public List<MovieReview> getAllReviewsForAdmin() {
        return movieReviewRepository.findAllByOrderByReviewDateDesc();
    }

    public List<MovieReview> searchReviewsForAdmin(String keyword,
                                                   String status,
                                                   LocalDate startDate,
                                                   LocalDate endDate) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);

        // Tìm kiếm quản trị bỏ dấu, không phân biệt hoa/thường và chấp nhận bí danh trạng thái.
        return movieReviewRepository.findAllByOrderByReviewDateDesc().stream()
                .filter(review -> matchesStatus(review, normalizedStatus))
                .filter(review -> matchesDateRange(review, startDate, endDate))
                .filter(review -> matchesKeyword(review, normalizedKeyword))
                .toList();
    }

    public long getTotalReviewCount() {
        return movieReviewRepository.count();
    }

    public long getVisibleReviewCount() {
        return movieReviewRepository.countByModerationStatus(VISIBLE_STATUS);
    }

    public Optional<MovieReview> getUserReview(int movieId, Integer accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return movieReviewRepository.findByMovieIdAndAccountAccountID(movieId, accountId);
    }

    public boolean canReviewMovie(Integer accountId, int movieId) {
        // Chỉ booking PAID với ngày/giờ chiếu đã qua mới cấp quyền mở form đánh giá.
        return accountId != null && bookingRepository.existsWatchedMovie(
                accountId,
                movieId,
                Booking.Status.PAID.name(),
                LocalDate.now(),
                LocalTime.now()
        );
    }

    @Transactional
    public void submitReview(int movieId, int accountId, int ratingScore, String comment) {
        /*
         * Xác minh điểm số, phim, tài khoản và bằng chứng đã xem trước khi ghi dữ liệu.
         * Mỗi tài khoản chỉ có một review cho một phim; gửi lại sẽ cập nhật bản ghi cũ
         * và đưa trạng thái về APPROVED để phản ánh nội dung mới nhất.
         */
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

        // Tái sử dụng bản ghi giúp giữ ràng buộc một người–một đánh giá cho mỗi phim.
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

    @Transactional
    public void setReviewVisible(Long reviewId, int adminAccountId, boolean visible) {
        // Không xóa vật lý: REJECTED tương ứng ẩn, APPROVED tương ứng hiển thị lại.
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
        // Chuẩn hóa cả Đ/đ vì Normalizer không tự chuyển hai ký tự riêng của tiếng Việt này.
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
