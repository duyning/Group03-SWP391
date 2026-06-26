/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: MovieService.java (API)
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-23
 * Chi tiết thay đổi:
 * - Đồng bộ hóa trạng thái phim: Đặt status = STOPPED khi ẩn phim (active = false)
 *   và khôi phục trạng thái phù hợp (COMING_SOON / NOW_SHOWING) khi kích hoạt lại.
 * - Đồng bộ hóa cập nhật thông tin: Nếu cập nhật status sang STOPPED thì tự động ẩn phim.
 */
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

    @Transactional
    public List<Movie> getAllMovies() {
        autoUpdateMovieStatuses();
        return movieRepository.findAll();
    }

    @Transactional
    public Optional<Movie> getMovieById(Integer id) {
        autoUpdateMovieStatuses();
        return movieRepository.findById(id);
    }

    @Transactional
    public List<Movie> searchMovies(String title,
                                    String genre,
                                    String director,
                                    Integer duration,
                                    String status,
                                    LocalDate releaseDate) {
        autoUpdateMovieStatuses();
        Movie.MovieStatus movieStatus = null;
        Boolean active = null;
        if (status != null && !status.isBlank()) {
            if ("Ngừng chiếu".equalsIgnoreCase(status.trim())) {
                active = false;
            } else {
                movieStatus = Movie.MovieStatus.fromJson(status);
                active = true;
            }
        }
        return movieRepository.searchMovies(title, genre, director, duration, movieStatus, releaseDate, active);
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
            // [SỬA - TrienLX - 2026-06-23]: Tự động đồng bộ cờ hiển thị active dựa trên trạng thái phim
            // Nếu lưu trạng thái là STOPPED (Ngừng chiếu) thì tự động ẩn phim, ngược lại kích hoạt lại hiển thị.
            if (updatedMovie.getStatus() == Movie.MovieStatus.STOPPED) {
                movie.setActive(false);
            } else {
                movie.setActive(true);
            }
            Movie savedMovie = movieRepository.save(movie);
            saveSuggestions(savedMovie);
            return savedMovie;
        }).orElseThrow(() -> new RuntimeException("Movie not found with id " + id));
    }

    @Transactional
    public void deleteMovie(Integer id) {
        movieRepository.findById(id).ifPresent(movie -> {
            // [SỬA - TrienLX - 2026-06-23]: Khi xóa mềm phim, set active = false và đồng bộ trạng thái STOPPED
            movie.setActive(false);
            movie.setStatus(Movie.MovieStatus.STOPPED);
            movieRepository.save(movie);
        });
    }

    /**
     * Đảo ngược trạng thái hiển thị của phim (active ↔ inactive).
     * Nếu phim đang hiển thị (active = true) thì tạm ẩn (active = false) và ngược lại.
     */
    @Transactional
    public Movie toggleActive(Integer id) {
        return movieRepository.findById(id).map(movie -> {
            boolean newActive = !movie.isActive();
            movie.setActive(newActive);
            // [SỬA - TrienLX - 2026-06-23]:
            // - Nếu ẩn phim: chuyển trạng thái phim sang STOPPED (Ngừng chiếu)
            // - Nếu mở lại: tính toán khôi phục trạng thái phù hợp dựa trên ngày khởi chiếu so với hôm nay
            if (!newActive) {
                movie.setStatus(Movie.MovieStatus.STOPPED);
            } else {
                LocalDate today = LocalDate.now();
                if (movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(today)) {
                    movie.setStatus(Movie.MovieStatus.COMING_SOON);
                } else {
                    movie.setStatus(Movie.MovieStatus.NOW_SHOWING);
                }
            }
            return movieRepository.save(movie);
        }).orElseThrow(() -> new RuntimeException("Không tìm thấy phim với ID: " + id));
    }

    @Transactional
    public Map<String, Long> getMovieStats() {
        autoUpdateMovieStatuses();
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", movieRepository.count());
        stats.put("nowShowing", movieRepository.countByStatus(Movie.MovieStatus.NOW_SHOWING));
        stats.put("upcoming", movieRepository.countByStatus(Movie.MovieStatus.COMING_SOON));
        stats.put("specialShow", movieRepository.countByStatus(Movie.MovieStatus.SPECIAL_SCREENING));
        stats.put("inactive", movieRepository.countByActiveFalse());
        return stats;
    }

    @Transactional
    public void autoUpdateMovieStatuses() {
        LocalDate today = LocalDate.now();
        // Cập nhật các phim từ Sắp chiếu sang Đang chiếu nếu ngày chiếu đã đến (chỉ áp dụng với phim active = true)
        movieRepository.autoUpdateUpcomingToNowShowing(
                today,
                Movie.MovieStatus.NOW_SHOWING,
                Movie.MovieStatus.COMING_SOON
        );
        // [SỬA - TrienLX - 2026-06-23]: Truyền thêm tham số MovieStatus.STOPPED để cập nhật trạng thái trong SQL an toàn
        movieRepository.autoDeactivateExpiredMovies(today, Movie.MovieStatus.STOPPED);
    }
}
