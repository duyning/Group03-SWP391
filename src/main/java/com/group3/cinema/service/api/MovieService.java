package com.group3.cinema.service.api;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.MovieRepository;
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

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
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

    @Transactional
    public Movie saveMovie(Movie movie) {
        return movieRepository.save(movie);
    }

    @Transactional
    public Movie updateMovie(Integer id, Movie updatedMovie) {
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
            movie.setActive(updatedMovie.isActive());
            return movieRepository.save(movie);
        }).orElseThrow(() -> new RuntimeException("Movie not found with id " + id));
    }

    @Transactional
    public void deleteMovie(Integer id) {
        movieRepository.deleteById(id);
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
