package com.group3.cinema.service;

/*
 * Nghiệp vụ dùng chung cho trang chủ, danh sách phim, chi tiết phim và tìm kiếm.
 * Service chủ động đồng bộ trạng thái phim theo ngày trước khi trả dữ liệu cho khách hàng.
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class MovieService {

    @Autowired
    // Repository cung cấp query phim active, theo trạng thái và các lệnh cập nhật trạng thái hàng loạt.
    private MovieRepository movieRepository;

    @Transactional
    public List<Movie> getHotMovies() {
        // Đồng bộ COMING_SOON/NOW_SHOWING/STOPPED trước khi chọn nội dung trang chủ.
        autoUpdateMovieStatuses();

        // Dùng cùng một mốc hiện tại để cửa sổ thống kê luôn chính xác 14 ngày.
        LocalDateTime toDate = LocalDateTime.now();
        LocalDateTime fromDate = toDate.minusDays(14);

        // Chỉ cộng tiền vé của booking PAID; không cộng combo/món lẻ và loại đơn hủy/hoàn tiền.
        return movieRepository.findHotMoviesByTicketRevenue(
                fromDate,
                toDate,
                PageRequest.of(0, 5)
        );
    }

    public List<Movie> getNowShowingMovies() {
        // Tái sử dụng method chung với enum NOW_SHOWING.
        return getMoviesByStatus(Movie.MovieStatus.NOW_SHOWING);
    }

    public List<Movie> getComingSoonMovies() {
        // Tái sử dụng method chung với enum COMING_SOON.
        return getMoviesByStatus(Movie.MovieStatus.COMING_SOON);
    }

    public List<Movie> getSpecialScreeningMovies() {
        // Tái sử dụng method chung với enum SPECIAL_SCREENING.
        return getMoviesByStatus(Movie.MovieStatus.SPECIAL_SCREENING);
    }

    @Transactional
    public Movie getMovieDetail(int id) {
        // Đồng bộ trạng thái trước để phim vừa hết lịch không còn mở được bằng URL cũ.
        autoUpdateMovieStatuses();

        // Optional rỗng được đổi thành null để controller quyết định redirect.
        return movieRepository.findByIdAndActiveTrue(id).orElse(null);
    }

    @Transactional
    public List<Movie> getMoviesByStatus(Movie.MovieStatus status) {
        // Trạng thái phụ thuộc ngày/suất nên phải cập nhật trước khi query tab.
        autoUpdateMovieStatuses();

        // Derived query đồng thời yêu cầu status và active=true.
        return movieRepository.findByStatusAndActiveTrue(status);
    }

    @Transactional
    public List<Movie> searchMovies(String keyword, String genre, String status) {
        // Đồng bộ trạng thái trước khi trả kết quả search kiểu cũ.
        autoUpdateMovieStatuses();

        // Chuyển chuỗi enum trong query parameter thành MovieStatus hoặc null.
        Movie.MovieStatus movieStatus = parseStatus(status);

        // status có nội dung nhưng parse thất bại nghĩa là request không thể khớp phim nào.
        if (trimToNull(status) != null && movieStatus == null) {
            return Collections.emptyList();
        }

        // null parameter làm điều kiện tương ứng trong JPQL được bỏ qua.
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
        // Trạng thái phim có thể đổi theo ngày trước mỗi lần tìm kiếm.
        autoUpdateMovieStatuses();

        // Parse status một lần để dùng trong predicate.
        Movie.MovieStatus movieStatus = parseStatus(status);

        // Phân biệt "không chọn status" với "status rác": trường hợp sau trả empty.
        if (trimToNull(status) != null && movieStatus == null) {
            return Collections.emptyList();
        }

        // Tạo stream từ tập phim active vì nhiều cột chứa chuỗi đa giá trị cần xử lý linh hoạt.
        List<Movie> movies = movieRepository.findByActiveTrue().stream()
                // Mặc định ẩn STOPPED; chỉ cho thấy khi người dùng chủ động lọc đúng STOPPED.
                .filter(movie -> movie.getStatus() != Movie.MovieStatus.STOPPED || movieStatus == Movie.MovieStatus.STOPPED)
                // Keyword OR trên title/cast/director.
                .filter(movie -> matchesKeyword(movie, keyword))
                // Trong từng nhóm multi-select dùng OR.
                .filter(movie -> matchesAny(movie.getGenre(), genres))
                .filter(movie -> matchesAny(movie.getFormat(), formats))
                .filter(movie -> matchesAny(movie.getLanguage(), languages))
                .filter(movie -> matchesAge(movie.getAgeRating(), ageRatings))
                // Giữa các nhóm filter dùng AND do các lệnh filter nối tiếp.
                .filter(movie -> movieStatus == null || movie.getStatus() == movieStatus)
                // Thu vào ArrayList mutable để method sortMovies có thể sắp xếp tại chỗ.
                .collect(Collectors.toCollection(ArrayList::new));

        // Áp dụng thứ tự do query parameter sort yêu cầu.
        sortMovies(movies, sort);

        // Trả toàn bộ kết quả đã lọc; controller chịu trách nhiệm cắt trang.
        return movies;
    }

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

    public List<String> getActiveFormats() {
        return List.of("2D", "3D", "IMAX 2D", "IMAX 3D", "4DX", "ScreenX");
    }

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

    public List<String> getActiveAgeRatings() {
        return List.of("P", "K", "T13", "T16", "T18", "C");
    }

    public Movie.MovieStatus[] getMovieStatuses() {
        // values() cung cấp toàn bộ enum để Thymeleaf dựng dropdown trạng thái.
        return Movie.MovieStatus.values();
    }

    @Transactional
    public void autoUpdateMovieStatuses() {
        // Lấy ngày theo timezone máy chủ một lần để ba update dùng cùng mốc.
        java.time.LocalDate today = java.time.LocalDate.now();

        // Chuyển phim COMING_SOON có releaseDate <= today sang NOW_SHOWING.
        movieRepository.autoUpdateUpcomingToNowShowing(
                today,
                Movie.MovieStatus.NOW_SHOWING,
                Movie.MovieStatus.COMING_SOON
        );

        // Phim đã phát hành nhưng không còn suất từ hôm nay trở đi chuyển STOPPED và active=false.
        movieRepository.autoDeactivateExpiredMovies(today, Movie.MovieStatus.STOPPED);

        // Dọn trường hợp dữ liệu đã STOPPED nhưng active vẫn còn true.
        movieRepository.deactivateStoppedMovies(Movie.MovieStatus.STOPPED);
    }

    private String trimToNull(String value) {
        // null/rỗng/chuỗi chỉ có space đều được biểu diễn bằng null cho query động.
        if (value == null || value.isBlank()) {
            return null;
        }

        // Dữ liệu có nội dung được bỏ khoảng trắng hai đầu.
        return value.trim();
    }

    private Movie.MovieStatus parseStatus(String status) {
        // Chuẩn hóa input trước khi parse enum.
        String normalizedStatus = trimToNull(status);

        // Không chọn status là điều kiện tùy chọn, không phải lỗi.
        if (normalizedStatus == null) {
            return null;
        }

        try {
            // valueOf yêu cầu tên enum chính xác như NOW_SHOWING.
            return Movie.MovieStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException exception) {
            // Caller phân biệt input rác bằng cách kiểm tra chuỗi gốc có nội dung.
            return null;
        }
    }

    private boolean matchesKeyword(Movie movie, String keyword) {
        // Chuẩn hóa keyword một lần cho ba phép so sánh.
        String normalizedKeyword = normalize(keyword);

        // Không nhập keyword nghĩa là predicate này chấp nhận mọi phim.
        if (normalizedKeyword == null) {
            return true;
        }

        // Keyword khớp nếu nằm trong tiêu đề, diễn viên hoặc đạo diễn.
        return containsNormalized(movie.getTitle(), normalizedKeyword)
                || containsNormalized(movie.getCast(), normalizedKeyword)
                || containsNormalized(movie.getDirector(), normalizedKeyword);
    }

    private boolean matchesAny(String movieValue, List<String> selectedValues) {
        // Chuẩn hóa toàn bộ lựa chọn, đồng thời loại null/rỗng.
        List<String> selected = normalizeList(selectedValues);

        // Không chọn bộ lọc nào nghĩa là chấp nhận mọi phim ở tiêu chí đó.
        if (selected.isEmpty()) {
            return true;
        }

        // Chuẩn hóa chuỗi đa giá trị lưu trong Movie.
        String normalizedMovieValue = normalize(movieValue);

        // Phim thiếu dữ liệu không thể khớp một lựa chọn cụ thể.
        if (normalizedMovieValue == null) {
            return false;
        }

        // anyMatch tạo logic OR giữa các lựa chọn trong cùng nhóm.
        return selected.stream().anyMatch(normalizedMovieValue::contains);
    }

    private boolean matchesAge(String ageRating, List<String> selectedAges) {
        // Chuẩn hóa rồi bỏ dấu "+" để T13 và T13+ được coi tương đương.
        List<String> selected = normalizeList(selectedAges).stream()
                .map(value -> value.replace("+", ""))
                .toList();

        // Không chọn age filter thì chấp nhận mọi phim.
        if (selected.isEmpty()) {
            return true;
        }

        // Chuẩn hóa ageRating lấy từ entity.
        String normalizedAge = normalize(ageRating);

        // Thiếu ageRating không khớp bộ lọc đã chọn.
        if (normalizedAge == null) {
            return false;
        }

        // Chuẩn hóa dấu "+" phía dữ liệu phim.
        normalizedAge = normalizedAge.replace("+", "");

        // OR giữa các mức tuổi được chọn.
        return selected.stream().anyMatch(normalizedAge::contains);
    }

    private boolean containsNormalized(String value, String normalizedKeyword) {
        // Chuẩn hóa field database bằng cùng quy tắc với keyword.
        String normalizedValue = normalize(value);

        // null không khớp; contains hỗ trợ tìm kiếm một phần.
        return normalizedValue != null && normalizedValue.contains(normalizedKeyword);
    }

    private List<String> normalizeList(List<String> values) {
        // Query parameter multi-select có thể hoàn toàn không xuất hiện.
        if (values == null) {
            return Collections.emptyList();
        }

        // Chuẩn hóa từng mục, bỏ null/rỗng và materialize thành list.
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
        // Tái sử dụng quy tắc null/rỗng và trim.
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        // NFD tách dấu tiếng Việt khỏi ký tự cơ sở, regex xóa dấu kết hợp.
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Đ/đ cần xử lý riêng; Locale.ROOT tránh lowercase phụ thuộc locale máy chủ.
        return normalized
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT);
    }

    private void sortMovies(List<Movie> movies, String sort) {
        // Chuẩn hóa sort; null hoặc giá trị lạ sẽ đi đến comparator mặc định cuối method.
        String normalizedSort = trimToNull(sort);

        // Ngày phát hành tăng dần, null nằm cuối.
        if ("releaseDate".equals(normalizedSort)) {
            movies.sort(Comparator.comparing(Movie::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }

        // Tiêu đề A-Z, không phân biệt hoa thường.
        if ("titleAsc".equals(normalizedSort)) {
            movies.sort(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER));
            return;
        }

        // ID lớn hơn được coi là bản ghi mới hơn.
        if ("newest".equals(normalizedSort)) {
            movies.sort(Comparator.comparing(Movie::getId).reversed());
            return;
        }

        // Featured: trạng thái ưu tiên trước, sau đó releaseDate mới và ID mới.
        movies.sort(Comparator
                .comparing((Movie movie) -> statusPriority(movie.getStatus()))
                .thenComparing(Movie::getReleaseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Movie::getId, Comparator.reverseOrder()));
    }

    private int statusPriority(Movie.MovieStatus status) {
        // Giá trị nhỏ đứng trước trong comparator.
        if (status == Movie.MovieStatus.NOW_SHOWING) {
            return 0;
        }

        // Suất đặc biệt đứng sau đang chiếu.
        if (status == Movie.MovieStatus.SPECIAL_SCREENING) {
            return 1;
        }

        // Sắp chiếu đứng sau hai nhóm đang có thể mua vé.
        if (status == Movie.MovieStatus.COMING_SOON) {
            return 2;
        }

        // null/STOPPED/giá trị khác đứng cuối.
        return 3;
    }
}
