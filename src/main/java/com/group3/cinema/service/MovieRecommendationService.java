package com.group3.cinema.service;

/*
 * Added on 2026-07-10: Customer movie recommendation scoring for list, search, and detail pages.
 * The score combines watched genres, global booking popularity, and movie availability status.
 * Created by: HuyPB - HE191335
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
        // Personal signals come from paid bookings; guests fall back to popularity/status signals.
        Set<Integer> watchedMovieIds = accountId == null
                ? Set.of()
                : new HashSet<>(bookingRepository.findPaidMovieIdsByAccount(accountId, Booking.Status.PAID));
        Set<String> preferredGenres = accountId == null
                ? Set.of()
                : normalizeGenres(bookingRepository.findPaidMovieGenresByAccount(accountId, Booking.Status.PAID));
        Map<Integer, Long> popularCounts = loadPopularCounts();

        List<MovieRecommendation> recommendations = new ArrayList<>();
        for (Movie movie : movieRepository.findByActiveTrue()) {
            // Do not recommend the current detail movie or movies the user has already watched.
            if (currentMovieId != null && movie.getId() == currentMovieId) {
                continue;
            }
            if (watchedMovieIds.contains(movie.getId())) {
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
        // Keep the score bounded to 0-100 for simple display in Thymeleaf templates.
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
