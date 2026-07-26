package com.group3.cinema.service;

/*
 * Tính điểm gợi ý phim cho trang danh sách, tìm kiếm và chi tiết phim.
 * Điểm kết hợp sở thích thể loại từ lịch sử xem, độ phổ biến toàn hệ thống
 * và trạng thái đang có thể mua vé của phim.
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

    // Cung cấp tập phim active để tạo các ứng viên đề xuất.
    private final MovieRepository movieRepository;

    // Cung cấp lịch sử PAID và thống kê số booking theo phim.
    private final BookingRepository bookingRepository;

    public MovieRecommendationService(MovieRepository movieRepository,
                                      BookingRepository bookingRepository) {
        // Lưu hai dependency được Spring truyền vào qua constructor.
        this.movieRepository = movieRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<MovieRecommendation> recommendMovies(Integer accountId, Integer currentMovieId, int limit) {
        // Khách chưa đăng nhập nhận empty set; user đăng nhập chỉ lấy phim trong booking PAID.
        Set<Integer> watchedMovieIds = accountId == null
                ? Set.of()
                : new HashSet<>(bookingRepository.findPaidMovieIdsByAccount(accountId, Booking.Status.PAID));

        // Lấy thể loại của lịch sử PAID rồi chuẩn hóa thành tập từ khóa không dấu/chữ thường.
        Set<String> preferredGenres = accountId == null
                ? Set.of()
                : normalizeGenres(bookingRepository.findPaidMovieGenresByAccount(accountId, Booking.Status.PAID));

        // Map movieId → số booking PAID là tín hiệu độ phổ biến toàn hệ thống.
        Map<Integer, Long> popularCounts = loadPopularCounts();

        // List mutable dùng để cộng từng ứng viên trước khi sort.
        List<MovieRecommendation> recommendations = new ArrayList<>();

        // Duyệt toàn bộ phim đang được phép hiển thị công khai.
        for (Movie movie : movieRepository.findByActiveTrue()) {
            // Trang chi tiết không được tự gợi ý lại chính phim đang mở.
            if (currentMovieId != null && movie.getId() == currentMovieId) {
                continue;
            }

            // Không đề xuất lại phim đã nằm trong lịch sử mua vé thành công của user.
            if (watchedMovieIds.contains(movie.getId())) {
                continue;
            }

            // Phim đã dừng chiếu không có giá trị mua vé nên bị loại.
            if (movie.getStatus() == Movie.MovieStatus.STOPPED) {
                continue;
            }

            // Một chuỗi thể loại có thể chứa nhiều mục nên phải tách và chuẩn hóa.
            Set<String> movieGenres = normalizeGenres(List.of(movie.getGenre()));

            // genreMatch chỉ true khi user có lịch sử và có ít nhất một thể loại giao nhau.
            boolean genreMatch = !preferredGenres.isEmpty() && movieGenres.stream().anyMatch(preferredGenres::contains);

            // Phim chưa từng được đặt có popularCount mặc định bằng 0.
            long popularCount = popularCounts.getOrDefault(movie.getId(), 0L);

            // Tính điểm số nguyên 0..100 theo trọng số nghiệp vụ.
            int score = calculateScore(movie, genreMatch, popularCount);

            // Sinh câu giải thích tương ứng với các tín hiệu đã góp vào đề xuất.
            String reason = buildReason(genreMatch, popularCount);

            // DTO giữ Movie cùng điểm/lý do để Thymeleaf render trực tiếp.
            recommendations.add(new MovieRecommendation(movie, score, reason));
        }

        // Ưu tiên điểm cao, sau đó phim phát hành mới hơn, cuối cùng ID mới hơn.
        recommendations.sort(Comparator
                .comparingInt(MovieRecommendation::recommendationScore).reversed()
                .thenComparing(item -> item.movie().getReleaseDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(item -> item.movie().getId(), Comparator.reverseOrder()));

        // limit âm được kéo về 0; toList trả danh sách kết quả không cần sửa tiếp.
        return recommendations.stream()
                .limit(Math.max(limit, 0))
                .toList();
    }

    private int calculateScore(Movie movie, boolean genreMatch, long popularCount) {
        // Mọi phim hợp lệ bắt đầu với điểm nền 20.
        int score = 20;

        // Sở thích cá nhân là tín hiệu mạnh nhất nên cộng 50.
        if (genreMatch) {
            score += 50;
        }

        // Mỗi booking PAID cộng 8 nhưng tín hiệu phổ biến bị chặn tối đa 40.
        score += (int) Math.min(popularCount * 8, 40);

        // Phim đang chiếu được ưu tiên hơn phim sắp chiếu.
        if (movie.getStatus() == Movie.MovieStatus.NOW_SHOWING) {
            score += 10;
        }

        // Suất đặc biệt nhận mức ưu tiên nhỏ hơn NOW_SHOWING.
        if (movie.getStatus() == Movie.MovieStatus.SPECIAL_SCREENING) {
            score += 6;
        }

        // Chặn 100 để UI có thể hiển thị như phần trăm phù hợp.
        return Math.min(score, 100);
    }

    private String buildReason(boolean genreMatch, long popularCount) {
        // Có cả hai tín hiệu thì giải thích đầy đủ nhất.
        if (genreMatch && popularCount > 0) {
            return "Phù hợp thể loại bạn đã xem và đang được nhiều khách đặt vé";
        }

        // Chỉ có lịch sử thể loại.
        if (genreMatch) {
            return "Phù hợp thể loại bạn thường xem";
        }

        // Chỉ có độ phổ biến, thường gặp với khách chưa đăng nhập.
        if (popularCount > 0) {
            return "Đang được nhiều khách đặt vé";
        }

        // Không có tín hiệu cá nhân/phổ biến thì dùng lý do mặc định.
        return "Gợi ý phim đang chiếu";
    }

    private Map<Integer, Long> loadPopularCounts() {
        // Khởi tạo map rỗng để gom projection từ repository.
        Map<Integer, Long> counts = new HashMap<>();

        // Mỗi Object[] do JPQL trả về có cột 0 = movieId, cột 1 = COUNT(booking).
        for (Object[] row : bookingRepository.countPaidBookingsByMovie(Booking.Status.PAID)) {
            // Kiểm tra độ dài và kiểu Number để tránh ClassCastException từ projection.
            if (row.length >= 2 && row[0] instanceof Number movieId && row[1] instanceof Number count) {
                // Chuẩn hóa ID về int và COUNT về long trước khi lưu.
                counts.put(movieId.intValue(), count.longValue());
            }
        }

        // Trả map tra cứu O(1) cho vòng lặp ứng viên.
        return counts;
    }

    private Set<String> normalizeGenres(List<String> genres) {
        // Set tự loại thể loại trùng sau khi chuẩn hóa.
        Set<String> normalized = new HashSet<>();

        // Duyệt từng chuỗi genre lấy từ lịch sử hoặc một Movie.
        for (String genre : genres) {
            // Bỏ qua dữ liệu null/rỗng trong database.
            if (genre == null || genre.isBlank()) {
                continue;
            }

            // Tách nhiều thể loại được lưu chung bằng , ; | hoặc /.
            for (String part : genre.split("[,;|/]+")) {
                // Bỏ dấu, trim và chuyển chữ thường.
                String value = normalize(part);

                // Chỉ thêm giá trị có nội dung.
                if (value != null) {
                    normalized.add(value);
                }
            }
        }

        // Kết quả dùng để kiểm tra giao tập thể loại.
        return normalized;
    }

    private String normalize(String value) {
        // null/rỗng không tạo ra token thể loại.
        if (value == null || value.isBlank()) {
            return null;
        }

        // NFD tách dấu tiếng Việt khỏi ký tự gốc, regex sau đó xóa các dấu tách rời.
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Đ/đ không bị NFD tách nên xử lý riêng, rồi dùng Locale.ROOT để lowercase ổn định.
        return normalized
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT);
    }
}
