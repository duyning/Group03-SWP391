/**
 * Service xử lý logic nghiệp vụ quản lý Phim chiếu rạp (`MovieService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `MovieApiController` để xử lý các request CRUD danh mục phim, thay đổi trạng thái ẩn/hiện, tìm kiếm phân loại và tự động cập nhật trạng thái.
 * - Gọi đến các Repository:
 *   + `MovieRepository`: Truy vấn và thao tác lưu/xóa bản ghi phim (`searchMovies`, `save`, `findSoftDeletedByTitle`, `autoUpdateUpcomingToNowShowing`, `autoDeactivateExpiredMovies`).
 *   + `MoviePersonSuggestionRepository`: Lưu danh sách gợi ý Đạo diễn, Diễn viên, Nhà sản xuất (`savePersonSuggestions`).
 *   + `TicketRepository`: Kiểm tra xem phim đã phát sinh vé được đặt bán thành công hay chưa (`hasBookedTicketsForMovie`) để chặn chỉnh sửa/xóa.
 *   + `BookingRepository`: Kiểm tra đơn đặt vé active cho phim (`hasActiveBookingsForMovie`).
 * 
 * Khởi tạo bởi: TrienLX (23/06/2026)
 */
package com.group3.cinema.service.api;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.MoviePersonSuggestion;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.MoviePersonSuggestionRepository;
import com.group3.cinema.repository.TicketRepository;
import com.group3.cinema.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service("apiMovieService")
public class MovieService {

    private final MovieRepository movieRepository;
    private final MoviePersonSuggestionRepository suggestionRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.group3.cinema.repository.BookingTicketRepository bookingTicketRepository;

    public MovieService(MovieRepository movieRepository, MoviePersonSuggestionRepository suggestionRepository) {
        this.movieRepository = movieRepository;
        this.suggestionRepository = suggestionRepository;
    }

    /**
     * Lấy toàn bộ danh sách bộ phim trong hệ thống (tự động kích hoạt cập nhật trạng thái phim `autoUpdateMovieStatuses`).
     * 
     * @return Danh sách Movie chưa bị xóa mềm.
     */
    @Transactional
    public List<Movie> getAllMovies() {
        autoUpdateMovieStatuses();
        return movieRepository.findAll();
    }

    /**
     * Tìm chi tiết phim theo ID.
     * 
     * @param id ID của phim.
     * @return Optional chứa Movie nếu tìm thấy.
     */
    @Transactional
    public Optional<Movie> getMovieById(Integer id) {
        autoUpdateMovieStatuses();
        return movieRepository.findById(id);
    }

    /**
     * Tìm kiếm và lọc danh sách phim đa chỉ tiêu dành cho giao diện quản trị Admin.
     * 
     * @param title Tiêu đề phim.
     * @param genre Thể loại phim.
     * @param director Đạo diễn.
     * @param duration Thời lượng phim (phút).
     * @param status Trạng thái phim (Đang chiếu, Sắp chiếu, Ngừng chiếu...).
     * @param releaseDate Ngày khởi chiếu.
     * @return Danh sách các bộ phim khớp điều kiện.
     */
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

    /**
     * Kiểm tra tính hợp lệ dữ liệu nhập của phim (chặn tên rỗng, kiểm tra trùng lặp tiêu đề, poster, trailer).
     * 
     * @param movie Đối tượng phim cần kiểm tra.
     * @param id ID của phim (0 nếu tạo mới, khác 0 nếu cập nhật).
     * @throws IllegalArgumentException nếu dữ liệu vi phạm ràng buộc.
     */
    private String normalizeMovieTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    public void validateMovie(Movie movie, int id) {
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            throw new IllegalArgumentException("Tên phim không được để trống.");
        }

        String inputNormalized = normalizeMovieTitle(movie.getTitle());
        List<Movie> existingMovies = movieRepository.findAll();
        for (Movie m : existingMovies) {
            if (m.getId() != id && !m.isDeleted()) {
                if (normalizeMovieTitle(m.getTitle()).equals(inputNormalized)) {
                    throw new IllegalArgumentException("Tên phim đã tồn tại trong hệ thống.");
                }
            }
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

    /**
     * Tự động trích xuất và lưu danh sách gợi ý Đạo diễn, Nhà sản xuất, Diễn viên vào bảng `movie_person_suggestions`.
     */
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

    /**
     * Tách danh sách tên bằng dấu phẩy và lưu vào bảng gợi ý nhân sự phim nếu chưa tồn tại.
     */
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

    /**
     * Tạo mới hoặc Khôi phục một bộ phim đã bị xóa mềm trước đó.
     * 
     * @param movie Đối tượng chứa thông tin phim mới.
     * @return Bản ghi Movie đã được lưu vào CSDL.
     */
    @Transactional
    public Movie saveMovie(Movie movie) {
        validateMovie(movie, 0);
        Movie savedMovie = movieRepository.save(movie);
        saveSuggestions(savedMovie);
        return savedMovie;
    }

    /**
     * Cập nhật thông tin chi tiết của bộ phim chỉ định (chặn sửa nếu đã phát sinh vé được đặt `ticketRepository.hasBookedTicketsForMovie`).
     * 
     * @param id ID của phim.
     * @param updatedMovie Đối tượng chứa thông tin cập nhật mới.
     * @return Movie sau khi chỉnh sửa thành công.
     */
    @Transactional
    public Movie updateMovie(Integer id, Movie updatedMovie) {
        if (ticketRepository.hasBookedTicketsForMovie(id)) {
            throw new IllegalArgumentException("Không thể sửa đổi thông tin phim này vì đã có vé được đặt.");
        }
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

    /**
     * Xóa mềm bộ phim (đặt `deleted = true`, `active = false`, `status = STOPPED`).
     * Chặn xóa nếu phim đã được bán vé hoặc đang có khách hàng thanh toán giữ chỗ.
     * 
     * @param id ID phim cần xóa.
     */
    @Transactional
    public void deleteMovie(Integer id) {
        movieRepository.findById(id).ifPresent(movie -> {
            if (ticketRepository.hasBookedTicketsForMovie(id)) {
                throw new IllegalArgumentException("Không thể xóa phim này vì đã có vé được đặt.");
            }
            LocalDateTime now = LocalDateTime.now();
            if (bookingRepository.hasActiveBookingsForMovie(id, now) || bookingTicketRepository.hasActiveHoldingsOrBookingsForMovie(id, now)) {
                throw new IllegalArgumentException("Không thể xóa phim này vì đang có khách hàng giữ ghế/thực hiện mua vé.");
            }
            movie.setDeleted(true);
            movie.setActive(false);
            movie.setStatus(Movie.MovieStatus.STOPPED);
            movieRepository.save(movie);
        });
    }

    /**
     * Đảo ngược trạng thái hiển thị của phim (active ↔ inactive).
     * 
     * @param id ID của phim.
     * @return Đối tượng Movie sau khi đổi cờ active.
     */
    @Transactional
    public Movie toggleActive(Integer id) {
        return movieRepository.findById(id).map(movie -> {
            boolean newActive = !movie.isActive();
            if (!newActive) {
                if (ticketRepository.hasBookedTicketsForMovie(id)) {
                    throw new IllegalArgumentException("Không thể tạm ẩn phim này vì đã có vé được đặt.");
                }
                LocalDateTime now = LocalDateTime.now();
                if (bookingRepository.hasActiveBookingsForMovie(id, now) || bookingTicketRepository.hasActiveHoldingsOrBookingsForMovie(id, now)) {
                    throw new IllegalArgumentException("Không thể tạm ẩn phim này vì đang có khách hàng giữ ghế/thực hiện mua vé.");
                }
            }
            movie.setActive(newActive);
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

    /**
     * Thống kê tổng số lượng phim phân theo các trạng thái trình chiếu (tổng số, đang chiếu, sắp chiếu, chiếu đặc biệt, tạm ẩn).
     * 
     * @return Map chứa các số liệu thống kê.
     */
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

    /**
     * Tự động cập nhật trạng thái các bộ phim dựa trên ngày khởi chiếu và tình trạng suất chiếu.
     */
    @Transactional
    public void autoUpdateMovieStatuses() {
        LocalDate today = LocalDate.now();
        movieRepository.autoUpdateUpcomingToNowShowing(
                today,
                Movie.MovieStatus.NOW_SHOWING,
                Movie.MovieStatus.COMING_SOON
        );
        movieRepository.autoDeactivateExpiredMovies(today, Movie.MovieStatus.STOPPED);
        movieRepository.deactivateStoppedMovies(Movie.MovieStatus.STOPPED);
    }
}

