package com.group3.cinema.service.api;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.MoviePersonSuggestion;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.MoviePersonSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service("apiMovieService")
public class MovieService {

    private final MovieRepository movieRepository;
    private final MoviePersonSuggestionRepository suggestionRepository;

    public MovieService(MovieRepository movieRepository, MoviePersonSuggestionRepository suggestionRepository) {
        this.movieRepository = movieRepository;
        this.suggestionRepository = suggestionRepository;
    }

    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    public Optional<Movie> getMovieById(Integer id) {
        return movieRepository.findById(id);
    }

    public List<Movie> searchMovies(String title,
                                    String genre,
                                    String director,
                                    Integer duration,
                                    String status,
                                    LocalDate releaseDate) {
        Movie.MovieStatus movieStatus = Movie.MovieStatus.fromJson(status);
        return movieRepository.searchMovies(title, genre, director, duration, movieStatus, releaseDate);
    }

    public void validateMovie(Movie movie, int id) {
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            throw new IllegalArgumentException("Tên phim không được để trống.");
        }
        if (movieRepository.existsDuplicateTitle(movie.getTitle().trim(), id)) {
            throw new IllegalArgumentException("Tên phim đã tồn tại trong hệ thống.");
        }
        if (movie.getPosterUrl() != null && !movie.getPosterUrl().isBlank()) {
            if (movieRepository.existsDuplicatePoster(movie.getPosterUrl().trim(), id)) {
                throw new IllegalArgumentException("Ảnh poster phim đã tồn tại trong hệ thống.");
            }
        }
        if (movie.getTrailerUrl() != null && !movie.getTrailerUrl().isBlank()) {
            if (movieRepository.existsDuplicateTrailer(movie.getTrailerUrl().trim(), id)) {
                throw new IllegalArgumentException("Trailer video đã tồn tại trong hệ thống.");
            }
        }
    }

    private void saveSuggestions(Movie movie) {
        if (movie.getDirector() != null) {
            savePersonSuggestions(movie.getDirector(), "DIRECTOR");
        }
        if (movie.getProducer() != null) {
            savePersonSuggestions(movie.getProducer(), "PRODUCER");
        }
        if (movie.getActors() != null) {
            savePersonSuggestions(movie.getActors(), "ACTOR");
        }
    }

    private void savePersonSuggestions(String rawNames, String type) {
        String[] names = rawNames.split(",");
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                if (suggestionRepository.findByNameIgnoreCaseAndTypeIgnoreCase(trimmed, type).isEmpty()) {
                    suggestionRepository.save(new MoviePersonSuggestion(trimmed, type));
                }
            }
        }
    }

    @Transactional
    public Movie saveMovie(Movie movie) {
        validateMovie(movie, 0);
        Movie savedMovie = movieRepository.save(movie);
        saveSuggestions(savedMovie);
        return savedMovie;
    }

    @Transactional
    public Movie updateMovie(Integer id, Movie updatedMovie) {
        validateMovie(updatedMovie, id);
        return movieRepository.findById(id).map(movie -> {
            movie.setTitle(updatedMovie.getTitle());
            movie.setTrailerUrl(updatedMovie.getTrailerUrl());
            movie.setSummary(updatedMovie.getSummary());
            movie.setGenre(updatedMovie.getGenre());
            movie.setDuration(updatedMovie.getDuration());
            movie.setDirector(updatedMovie.getDirector());
            movie.setLanguage(updatedMovie.getLanguage());
            movie.setActors(updatedMovie.getActors());
            movie.setPosterUrl(updatedMovie.getPosterUrl());
            movie.setReleaseDate(updatedMovie.getReleaseDate());
            movie.setStatus(updatedMovie.getStatus());
            movie.setBannerUrl(updatedMovie.getBannerUrl());
            movie.setAgeRating(updatedMovie.getAgeRating());
            movie.setReleaseYear(updatedMovie.getReleaseYear());
            movie.setProducer(updatedMovie.getProducer());
            movie.setFormat(updatedMovie.getFormat());
            movie.setActive(updatedMovie.isActive());
            Movie savedMovie = movieRepository.save(movie);
            saveSuggestions(savedMovie);
            return savedMovie;
        }).orElseThrow(() -> new RuntimeException("Movie not found with id " + id));
    }

    @Transactional
    public void deleteMovie(Integer id) {
        movieRepository.findById(id).ifPresent(movie -> {
            movie.setActive(false);
            movieRepository.save(movie);
        });
    }

    public Map<String, Long> getMovieStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", movieRepository.count());
        stats.put("nowShowing", movieRepository.countByStatus(Movie.MovieStatus.NOW_SHOWING));
        stats.put("upcoming", movieRepository.countByStatus(Movie.MovieStatus.COMING_SOON));
        stats.put("specialShow", movieRepository.countByStatus(Movie.MovieStatus.SPECIAL_SCREENING));
        return stats;
    }
}
