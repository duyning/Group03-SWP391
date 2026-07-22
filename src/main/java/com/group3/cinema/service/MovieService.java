/**
 * Service xử lý tìm kiếm, lọc phim công khai và Banner ngẫu nhiên cho Khách hàng (`MovieService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PublicController`, `CustomerMovieController`.
 * - Tương tác với `MovieRepository` để tra cứu danh sách phim theo trạng thái (`findByStatusAndActiveTrue`), tìm kiếm lọc đa tiêu chí (`searchActiveMovies`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335, TrienLX
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** Lấy ngẫu nhiên 5 bộ phim đang mở chiếu để hiển thị Slide Banner trên trang chủ. */
    @Transactional
    public List<Movie> getRandomBannerMovies() {
        autoUpdateMovieStatuses();
        List<Movie> movies = movieRepository.findByActiveTrue();
        Collections.shuffle(movies);
        return movies.stream()
                .filter(m -> m.getStatus() != Movie.MovieStatus.STOPPED)
                .limit(5)
                .collect(Collectors.toList());
    }

    /** Lấy danh sách các bộ phim Đang chiếu (`NOW_SHOWING`). */
    public List<Movie> getNowShowingMovies() {
        return getMoviesByStatus(Movie.MovieStatus.NOW_SHOWING);
    }

    /** Lấy danh sách các bộ phim Sắp chiếu (`COMING_SOON`). */
    public List<Movie> getComingSoonMovies() {
        return getMoviesByStatus(Movie.MovieStatus.COMING_SOON);
    }

    /** Lấy danh sách các bộ phim Suất chiếu đặc biệt (`SPECIAL_SCREENING`). */
    public List<Movie> getSpecialScreeningMovies() {
        return getMoviesByStatus(Movie.MovieStatus.SPECIAL_SCREENING);
    }

    /** Lấy thông tin chi tiết của bộ phim đang hoạt động theo ID. */
    @Transactional
    public Movie getMovieDetail(int id) {
        autoUpdateMovieStatuses();
        return movieRepository.findByIdAndActiveTrue(id).orElse(null);
    }

    /** Lấy danh sách các bộ phim active theo trạng thái chỉ định. */
    @Transactional
    public List<Movie> getMoviesByStatus(Movie.MovieStatus status) {
        autoUpdateMovieStatuses();
        return movieRepository.findByStatusAndActiveTrue(status);
    }

    /** Tìm kiếm phim theo từ khóa, thể loại và trạng thái đơn giản. */
    @Transactional
    public List<Movie> searchMovies(String keyword, String genre, String status) {
        autoUpdateMovieStatuses();
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

    /** Lọc danh sách phim nâng cao theo nhiều thuộc tính (Thể loại, Định dạng, Ngôn ngữ, Độ tuổi) và sắp xếp kết quả. */
    public List<Movie> searchMovies(String keyword,
                                    List<String> genres,
                                    List<String> formats,
                                    List<String> languages,
                                    List<String> ageRatings,
                                    String status,
                                    String sort) {
        autoUpdateMovieStatuses();
        Movie.MovieStatus movieStatus = parseStatus(status);
        if (trimToNull(status) != null && movieStatus == null) {
            return Collections.emptyList();
        }

        List<Movie> movies = movieRepository.findByActiveTrue().stream()
                .filter(movie -> movie.getStatus() != Movie.MovieStatus.STOPPED || movieStatus == Movie.MovieStatus.STOPPED)
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

    /** Lấy danh sách danh mục thể loại phim chuẩn. */
    public List<String> getActiveGenres() {
        return List.of(
                "Hành động",
                "Tình cảm",
                "Kinh dị",
                "Hài hước",
                "Hoạt hình",
                "Viễn tưởng",
                "Phiêu lưu",
                "Kịch tính",
                "Thần thoại",
                "Tội phạm",
                "Gia đình",
                "Nhạc kịch"
        );
    }

    /** Lấy danh sách định dạng phim chuẩn (2D, 3D, IMAX...). */
    public List<String> getActiveFormats() {
        return List.of("2D", "3D", "IMAX 2D", "IMAX 3D", "4DX", "ScreenX");
    }

    /** Lấy danh sách ngôn ngữ chuẩn. */
    public List<String> getActiveLanguages() {
        return List.of(
                "Tiếng Việt",
                "Lồng tiếng Tiếng Việt",
                "Tiếng Anh - Phụ đề Tiếng Việt & Tiếng Anh",
                "Tiếng Hàn - Phụ đề Tiếng Việt & Tiếng Anh",
                "Tiếng Nhật - Phụ đề Tiếng Việt & Tiếng Anh",
                "Tiếng Trung - Phụ đề Tiếng Việt",
                "Tiếng Thái - Phụ đề Tiếng Việt",
                "Tiếng Ấn Độ - Phụ đề Tiếng Việt",
                "Tiếng Pháp - Phụ đề Tiếng Việt",
                "Tiếng Tây Ban Nha - Phụ đề Tiếng Việt"
        );
    }

    /** Lấy danh sách độ tuổi phân loại chuẩn (P, K, T13, T16, T18, C). */
    public List<String> getActiveAgeRatings() {
        return List.of("P", "K", "T13", "T16", "T18", "C");
    }

    /** Lấy danh sách tất cả giá trị Enum MovieStatus. */
    public Movie.MovieStatus[] getMovieStatuses() {
        return Movie.MovieStatus.values();
    }

    /** Tự động đồng bộ chuyển trạng thái phim quá hạn sang Ngừng chiếu (`STOPPED`). */
    @Transactional
    public void autoUpdateMovieStatuses() {
        java.time.LocalDate today = java.time.LocalDate.now();
        movieRepository.autoUpdateUpcomingToNowShowing(
                today,
                Movie.MovieStatus.NOW_SHOWING,
                Movie.MovieStatus.COMING_SOON
        );
        movieRepository.autoDeactivateExpiredMovies(today, Movie.MovieStatus.STOPPED);
        movieRepository.deactivateStoppedMovies(Movie.MovieStatus.STOPPED);
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

    private List<String> distinctMovieValues(java.util.function.Function<Movie, String> getter) {
        return movieRepository.findByActiveTrue().stream()
                .map(getter)
                .flatMap(value -> splitMovieValue(value).stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> splitMovieValue(String value) {
        String cleanValue = trimToNull(value);
        if (cleanValue == null) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(cleanValue.split("[,;|/]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
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

