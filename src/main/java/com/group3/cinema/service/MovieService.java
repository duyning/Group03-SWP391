package com.group3.cinema.service;

/*
 * Added on 2026-06-04: Service logic for movie banners, status lists, detail, and search.
 * Updated on 2026-06-04: Added UC-G03 searchMovies(keyword, genre, status)
 * limited to movie title, genre, and status.
 * Updated on 2026-06-26: Expanded guest search filtering for pill UI by
 * keyword, genre, format, language/subtitle, age rating, status, and sort.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class MovieService {

    @Autowired
    private MovieRepository movieRepository;

    /**
     * Homepage banner flow:
     * 1. Load all active movies.
     * 2. Shuffle the list so the hero area can vary between requests.
     * 3. Return at most 5 movies for the banner carousel/hero section.
     */
    public List<Movie> getRandomBannerMovies() {
        List<Movie> movies = movieRepository.findByActiveTrue();
        Collections.shuffle(movies);
        return movies.stream()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Public listing flow for movies that customers can watch/book now.
     */
    public List<Movie> getNowShowingMovies() {
        return getMoviesByStatus(Movie.MovieStatus.NOW_SHOWING);
    }

    /**
     * Public listing flow for movies announced for a future release date.
     */
    public List<Movie> getComingSoonMovies() {
        return getMoviesByStatus(Movie.MovieStatus.COMING_SOON);
    }

    /**
     * Public listing flow for limited events or special screenings.
     */
    public List<Movie> getSpecialScreeningMovies() {
        return getMoviesByStatus(Movie.MovieStatus.SPECIAL_SCREENING);
    }

    /**
     * Movie detail flow:
     * - Return the movie only when it is active.
     * - Return null when the id does not exist or the movie is hidden.
     * The controller uses null to redirect customers back to the movie list.
     */
    public Movie getMovieDetail(int id) {
        return movieRepository.findByIdAndActiveTrue(id).orElse(null);
    }

    /**
     * Shared status filter used by all status-specific service methods.
     */
    public List<Movie> getMoviesByStatus(Movie.MovieStatus status) {
        return movieRepository.findByStatusAndActiveTrue(status);
    }

    /**
     * UC-G03 Search Movies:
     * Search active movies by title keyword, genre, and movie status only.
     */
    public List<Movie> searchMovies(String keyword, String genre, String status) {
        Movie.MovieStatus movieStatus = parseStatus(status);
        if (trimToNull(status) != null && movieStatus == null) {
            return Collections.emptyList();
        }

        return movieRepository.searchActiveMovies(
                trimToNull(keyword),
                trimToNull(genre),
                movieStatus
        );
    }

    public List<Movie> searchMovies(String keyword,
                                    List<String> genres,
                                    List<String> formats,
                                    List<String> languages,
                                    List<String> ageRatings,
                                    String status,
                                    String sort) {
        Movie.MovieStatus movieStatus = parseStatus(status);
        if (trimToNull(status) != null && movieStatus == null) {
            return Collections.emptyList();
        }

        List<Movie> movies = movieRepository.findByActiveTrue().stream()
                .filter(movie -> matchesKeyword(movie, keyword))
                .filter(movie -> matchesAny(movie.getGenre(), genres))
                .filter(movie -> matchesAny(movie.getFormat(), formats))
                .filter(movie -> matchesAny(movie.getLanguage(), languages))
                .filter(movie -> matchesAge(movie.getAgeRating(), ageRatings))
                .filter(movie -> movieStatus == null || movie.getStatus() == movieStatus)
                .collect(Collectors.toCollection(ArrayList::new));

        sortMovies(movies, sort);
        return movies;
    }

    public List<String> getActiveGenres() {
        return movieRepository.findDistinctActiveGenres();
    }

    public Movie.MovieStatus[] getMovieStatuses() {
        return Movie.MovieStatus.values();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Movie.MovieStatus parseStatus(String status) {
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus == null) {
            return null;
        }

        try {
            return Movie.MovieStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean matchesKeyword(Movie movie, String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword == null) {
            return true;
        }

        return containsNormalized(movie.getTitle(), normalizedKeyword)
                || containsNormalized(movie.getCast(), normalizedKeyword)
                || containsNormalized(movie.getDirector(), normalizedKeyword);
    }

    private boolean matchesAny(String movieValue, List<String> selectedValues) {
        List<String> selected = normalizeList(selectedValues);
        if (selected.isEmpty()) {
            return true;
        }

        String normalizedMovieValue = normalize(movieValue);
        if (normalizedMovieValue == null) {
            return false;
        }

        return selected.stream().anyMatch(normalizedMovieValue::contains);
    }

    private boolean matchesAge(String ageRating, List<String> selectedAges) {
        List<String> selected = normalizeList(selectedAges).stream()
                .map(value -> value.replace("+", ""))
                .toList();
        if (selected.isEmpty()) {
            return true;
        }

        String normalizedAge = normalize(ageRating);
        if (normalizedAge == null) {
            return false;
        }
        normalizedAge = normalizedAge.replace("+", "");

        return selected.stream().anyMatch(normalizedAge::contains);
    }

    private boolean containsNormalized(String value, String normalizedKeyword) {
        String normalizedValue = normalize(value);
        return normalizedValue != null && normalizedValue.contains(normalizedKeyword);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }

        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT);
    }

    private void sortMovies(List<Movie> movies, String sort) {
        String normalizedSort = trimToNull(sort);
        if ("releaseDate".equals(normalizedSort)) {
            movies.sort(Comparator.comparing(Movie::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        if ("titleAsc".equals(normalizedSort)) {
            movies.sort(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER));
            return;
        }
        if ("newest".equals(normalizedSort)) {
            movies.sort(Comparator.comparing(Movie::getId).reversed());
            return;
        }

        movies.sort(Comparator
                .comparing((Movie movie) -> statusPriority(movie.getStatus()))
                .thenComparing(Movie::getReleaseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Movie::getId, Comparator.reverseOrder()));
    }

    private int statusPriority(Movie.MovieStatus status) {
        if (status == Movie.MovieStatus.NOW_SHOWING) {
            return 0;
        }
        if (status == Movie.MovieStatus.SPECIAL_SCREENING) {
            return 1;
        }
        if (status == Movie.MovieStatus.COMING_SOON) {
            return 2;
        }
        return 3;
    }
}
