package example.service;

/*
 * Added on 2026-06-04: Service logic for movie banners, status lists, detail, and search.
 * Created by: HuyPB - HE191335
 */

import example.model.Movie;
import example.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
     * Search flow:
     * - Empty keyword means show all active movies.
     * - Non-empty keyword is normalized to lowercase and trimmed.
     * - Search checks common visible fields customers may remember:
     *   title, genre, director, cast, and language.
     */
    public List<Movie> searchMovies(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return movieRepository.findByActiveTrue();
        }

        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT).trim();
        return movieRepository.findByActiveTrue().stream()
                .filter(movie -> containsIgnoreCase(movie.getTitle(), normalizedKeyword)
                        || containsIgnoreCase(movie.getGenre(), normalizedKeyword)
                        || containsIgnoreCase(movie.getDirector(), normalizedKeyword)
                        || containsIgnoreCase(movie.getCast(), normalizedKeyword)
                        || containsIgnoreCase(movie.getLanguage(), normalizedKeyword))
                .collect(Collectors.toList());
    }

    /**
     * Null-safe case-insensitive string matching helper for searchMovies().
     */
    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
