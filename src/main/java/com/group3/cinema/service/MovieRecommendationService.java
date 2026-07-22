package com.group3.cinema.service;

/*
 * Tính điểm gợi ý phim cho trang danh sách, tìm kiếm và chi tiết phim.
 * Điểm kết hợp sở thích thể loại từ lịch sử xem, độ phổ biến toàn hệ thống
 * và trạng thái đang có thể mua vé của phim.
 */

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

    public List<MovieRecommendation> recommendMovies(Integer accountId, Integer currentMovieId, int limit) {
        /*
         * Tín hiệu cá nhân chỉ lấy từ booking PAID. Khách chưa đăng nhập có tập sở thích
         * rỗng nên tự động rơi về gợi ý theo độ phổ biến và trạng thái phim.
         */
        Set<Integer> watchedMovieIds = accountId == null
                ? Set.of()
                : new HashSet<>(bookingRepository.findPaidMovieIdsByAccount(accountId, Booking.Status.PAID));
        Set<String> preferredGenres = accountId == null
                ? Set.of()
                : normalizeGenres(bookingRepository.findPaidMovieGenresByAccount(accountId, Booking.Status.PAID));
        Map<Integer, Long> popularCounts = loadPopularCounts();

        List<MovieRecommendation> recommendations = new ArrayList<>();
        for (Movie movie : movieRepository.findByActiveTrue()) {
            // Không gợi ý lại phim đang xem chi tiết, phim đã xem hoặc phim đã dừng chiếu.
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

    private int calculateScore(Movie movie, boolean genreMatch, long popularCount) {
        /*
         * Trọng số: nền 20, trùng thể loại +50, mỗi lượt booking +8 (tối đa 40),
         * đang chiếu +10 và suất đặc biệt +6. Điểm cuối chặn ở 100 để dễ hiển thị.
         */
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
        // Repository trả projection Object[] gồm movieId và số booking PAID.
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : bookingRepository.countPaidBookingsByMovie(Booking.Status.PAID)) {
            if (row.length >= 2 && row[0] instanceof Number movieId && row[1] instanceof Number count) {
                counts.put(movieId.intValue(), count.longValue());
            }
        }
        return counts;
    }

    private Set<String> normalizeGenres(List<String> genres) {
        // Một phim có thể lưu nhiều thể loại phân cách bởi dấu phẩy, chấm phẩy, | hoặc /.
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
