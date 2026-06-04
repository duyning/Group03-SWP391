package example.service;

/*
 * Added on 2026-06-04: Service logic for movie banners, status lists, detail, and search.
 * Updated on 2026-06-04: Added UC-G03 searchMovies(keyword, genre, status)
 * limited to movie title, genre, and status.
 * Created by: HuyPB - HE191335
 */

import example.entity.Movie;
import example.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
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
}
