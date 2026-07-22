/**
 * Service tính toán và gợi ý Phim chiếu rạp cá nhân hóa dành cho Khách hàng (`MovieRecommendationService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CustomerMovieController` và `PublicController` khi hiển thị danh sách phim xem nhiều, phim gợi ý trên trang chủ hoặc trang chi tiết phim.
 * - Tương tác với:
 *   + `BookingRepository`: Trích xuất danh sách phim khách đã mua vé (`findPaidMovieIdsByAccount`), thể loại đã xem (`findPaidMovieGenresByAccount`) và thống kê lượt đặt vé toàn hệ thống (`countPaidBookingsByMovie`).
 *   + `MovieRepository`: Tra cứu phim đang hoạt động (`findByActiveTrue`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (10/07/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.dto.MovieRecommendation;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.MovieRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MovieRecommendationService {

    private final MovieRepository movieRepository;
    private final BookingRepository bookingRepository;

    public MovieRecommendationService(MovieRepository movieRepository,
                                      BookingRepository bookingRepository) {
        this.movieRepository = movieRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Tạo danh sách phim gợi ý phù hợp với sở thích khách hàng dựa trên lịch sử đặt vé và độ hot thị hiếu.
     * 
     * @param accountId ID tài khoản khách hàng (null nếu là khách vãng xuất chưa đăng nhập).
     * @param currentMovieId ID phim đang xem chi tiết (loại trừ khỏi danh sách gợi ý).
     * @param limit Số lượng phim gợi ý tối đa cần trả về.
     * @return Danh sách MovieRecommendation đã xếp hạng điểm cao xuống thấp.
     */
    public List<MovieRecommendation> recommendMovies(Integer accountId, Integer currentMovieId, int limit) {
        Set<Integer> watchedMovieIds = accountId == null
                ? Set.of()
                : new HashSet<>(bookingRepository.findPaidMovieIdsByAccount(accountId, Booking.Status.PAID));
        Set<String> preferredGenres = accountId == null
                ? Set.of()
                : normalizeGenres(bookingRepository.findPaidMovieGenresByAccount(accountId, Booking.Status.PAID));
        Map<Integer, Long> popularCounts = loadPopularCounts();

        List<MovieRecommendation> recommendations = new ArrayList<>();
        for (Movie movie : movieRepository.findByActiveTrue()) {
            if (currentMovieId != null && movie.getId() == currentMovieId) {
                continue;
            }
            if (watchedMovieIds.contains(movie.getId())) {
                continue;
            }
            if (movie.getStatus() == Movie.MovieStatus.STOPPED) {
                continue;
            }

            Set<String> movieGenres = normalizeGenres(List.of(movie.getGenre()));
            boolean genreMatch = !preferredGenres.isEmpty() && movieGenres.stream().anyMatch(preferredGenres::contains);
            long popularCount = popularCounts.getOrDefault(movie.getId(), 0L);
            int score = calculateScore(movie, genreMatch, popularCount);
            String reason = buildReason(genreMatch, popularCount);
            recommendations.add(new MovieRecommendation(movie, score, reason));
        }

        recommendations.sort(Comparator
                .comparingInt(MovieRecommendation::recommendationScore).reversed()
                .thenComparing(item -> item.movie().getReleaseDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(item -> item.movie().getId(), Comparator.reverseOrder()));

        return recommendations.stream()
                .limit(Math.max(limit, 0))
                .toList();
    }

    /**
     * Tính toán tổng điểm gợi ý (thang điểm 0-100) theo trùng thể loại thích (+50đ), lượt mua vé toàn rạp (tối đa +40đ), trạng thái phim đang chiếu (+10đ).
     */
    private int calculateScore(Movie movie, boolean genreMatch, long popularCount) {
        int score = 20;
        if (genreMatch) {
            score += 50;
        }
        score += (int) Math.min(popularCount * 8, 40);
        if (movie.getStatus() == Movie.MovieStatus.NOW_SHOWING) {
            score += 10;
        }
        if (movie.getStatus() == Movie.MovieStatus.SPECIAL_SCREENING) {
            score += 6;
        }
        return Math.min(score, 100);
    }

    /** Tạo văn bản giải thích lý do gợi ý phim tới khách hàng. */
    private String buildReason(boolean genreMatch, long popularCount) {
        if (genreMatch && popularCount > 0) {
            return "Phù hợp thể loại bạn đã xem và đang được nhiều khách đặt vé";
        }
        if (genreMatch) {
            return "Phù hợp thể loại bạn thường xem";
        }
        if (popularCount > 0) {
            return "Đang được nhiều khách đặt vé";
        }
        return "Gợi ý phim đang chiếu";
    }

    private Map<Integer, Long> loadPopularCounts() {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : bookingRepository.countPaidBookingsByMovie(Booking.Status.PAID)) {
            if (row.length >= 2 && row[0] instanceof Number movieId && row[1] instanceof Number count) {
                counts.put(movieId.intValue(), count.longValue());
            }
        }
        return counts;
    }

    private Set<String> normalizeGenres(List<String> genres) {
        Set<String> normalized = new HashSet<>();
        for (String genre : genres) {
            if (genre == null || genre.isBlank()) {
                continue;
            }
            for (String part : genre.split("[,;|/]+")) {
                String value = normalize(part);
                if (value != null) {
                    normalized.add(value);
                }
            }
        }
        return normalized;
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

