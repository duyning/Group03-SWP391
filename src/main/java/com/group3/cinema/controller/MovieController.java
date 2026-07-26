package com.group3.cinema.controller;

/**
 * LUỒNG CHẠY CHỨC NĂNG QUẢN LÝ PHIM (EXECUTION FLOW):
 * Trình duyệt -> MovieController -> MovieService -> MovieRepository -> Database (bảng movie) -> Render View HTML
 * 
 * Các bước xử lý:
 * 1. Xem danh sách phim khách hàng: GET /movies -> MovieController.listMovies() -> MovieService.findNowShowingMovies() / findComingSoonMovies() -> movie-list.html
 * 2. Tìm kiếm phim khách hàng: GET /search?keyword=... -> MovieController.searchMovies() -> MovieService.searchMovies() -> search-result.html
 * 3. Xem chi tiết phim: GET /movies/{id} -> MovieController.getMovieDetail() -> MovieService.getMovieById() -> movie-detail.html
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.MovieReview;
import com.group3.cinema.service.MovieRecommendationService;
import com.group3.cinema.service.MovieReviewService;
import com.group3.cinema.service.MovieService;
import com.group3.cinema.service.SeatHoldingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Controller
public class MovieController {

    @Autowired
    // Cung cấp nghiệp vụ đọc danh sách, chi tiết, tìm kiếm và tự đồng bộ trạng thái phim.
    private MovieService movieService;

    @Autowired
    // Cung cấp nghiệp vụ đọc, tạo/cập nhật và kiểm tra quyền đánh giá phim.
    private MovieReviewService movieReviewService;

    @Autowired
    // Tính danh sách phim đề xuất theo lịch sử mua vé và độ phổ biến.
    private MovieRecommendationService movieRecommendationService;

    @Autowired
    // Dùng để trả ghế khi người dùng rời luồng booking về danh sách phim.
    private SeatHoldingService seatHoldingService;

    /**
     * Hiển thị màn danh sách phim.
     *
     * <p>Phim được chia thành ba nhóm theo trạng thái để template dựng từng tab.
     * Danh sách gợi ý được cá nhân hóa theo tài khoản; khách chưa đăng nhập vẫn
     * nhận gợi ý dựa trên độ phổ biến chung.</p>
     */
    @GetMapping("/movies")
    public String showMovies(HttpSession session, Model model) {
        // Người dùng đã quay về catalog nên mọi ghế HOLDING của phiên booking cũ cần được trả lại.
        releaseHoldIfPresent(session);

        // Đưa tài khoản vào model cho header và nhận lại Account để cá nhân hóa đề xuất.
        Account loggedInUser = addLoggedInUser(session, model);

        // Tải nhóm NOW_SHOWING; MovieService sẽ tự cập nhật trạng thái phim trước khi query.
        model.addAttribute("nowShowingMovies", movieService.getNowShowingMovies());

        // Tải nhóm COMING_SOON cho tab "Phim sắp chiếu".
        model.addAttribute("comingSoonMovies", movieService.getComingSoonMovies());

        // Tải nhóm SPECIAL_SCREENING cho tab suất chiếu đặc biệt.
        model.addAttribute("specialScreeningMovies", movieService.getSpecialScreeningMovies());

        // accountId = null với khách vãng lai; service khi đó dùng độ phổ biến thay cho lịch sử cá nhân.
        model.addAttribute("recommendedMovies", movieRecommendationService.recommendMovies(
                // Chỉ đọc ID khi Account tồn tại để tránh NullPointerException.
                loggedInUser == null ? null : loggedInUser.getAccountID(),
                // currentMovieId = null vì màn danh sách không có phim hiện tại cần loại bỏ.
                null,
                // Chỉ lấy tối đa 6 thẻ đề xuất để giao diện không quá dài.
                6
        ));

        // Thymeleaf render templates/movie-list.html bằng các model attribute vừa tạo.
        return "movie-list";
    }

    /**
     * Hiển thị chi tiết phim, đánh giá và các phim được đề xuất liên quan.
     *
     * <p>Bộ lọc đánh giá được chuẩn hóa trước khi gọi service: số sao chỉ nhận 1–5,
     * khoảng ngày bị nhập ngược sẽ được đổi chỗ, số trang luôn bắt đầu từ 1 và
     * được kéo về trang cuối nếu vượt phạm vi. Cờ {@code canReview} cho giao diện
     * biết người dùng đã xem phim và đủ điều kiện gửi đánh giá hay chưa.</p>
     */
    @GetMapping("/movies/{id}")
    public String showMovieDetail(@PathVariable("id") int id,
                                  @RequestParam(value = "reviewRating", required = false) Integer reviewRating,
                                  @RequestParam(value = "reviewStartDate", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reviewStartDate,
                                  @RequestParam(value = "reviewEndDate", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reviewEndDate,
                                  @RequestParam(value = "reviewPage", required = false, defaultValue = "1") int reviewPage,
                                  HttpSession session,
                                  Model model) {
        // Đọc phim active từ database; phim đã ẩn/xóa/dừng sẽ trả null.
        Movie movie = movieService.getMovieDetail(id);

        // URL không hợp lệ được đưa về danh sách thay vì render template với movie = null.
        if (movie == null) {
            return "redirect:/movies";
        }

        // Thêm user vào model và giữ Account để kiểm tra quyền review/gợi ý cá nhân.
        Account loggedInUser = addLoggedInUser(session, model);

        // PageRequest đánh số từ 0 nhưng URL đánh số từ 1; trước hết chặn trang âm hoặc bằng 0.
        int normalizedReviewPage = Math.max(reviewPage, 1);

        // Chỉ chấp nhận đúng thang điểm 1..5; giá trị sửa tay ngoài miền được coi là không lọc.
        Integer normalizedReviewRating = reviewRating != null && reviewRating >= 1 && reviewRating <= 5
                ? reviewRating
                : null;

        // Nếu người dùng nhập ngược khoảng ngày, đổi hai đầu để truy vấn vẫn có ý nghĩa.
        if (reviewStartDate != null && reviewEndDate != null && reviewStartDate.isAfter(reviewEndDate)) {
            // Giữ giá trị ngày bắt đầu cũ trong biến tạm trước khi ghi đè.
            LocalDate originalStartDate = reviewStartDate;

            // Ngày kết thúc cũ trở thành ngày bắt đầu mới.
            reviewStartDate = reviewEndDate;

            // Ngày bắt đầu cũ trở thành ngày kết thúc mới.
            reviewEndDate = originalStartDate;
        }

        // Service đổi LocalDate thành khoảng LocalDateTime và repository chỉ lấy review APPROVED.
        Page<MovieReview> reviewPageData = movieReviewService.getApprovedReviews(
                // Chỉ lấy review thuộc bộ phim đang mở.
                id,
                // null nghĩa là nhận mọi mức sao.
                normalizedReviewRating,
                // null nghĩa là không giới hạn đầu khoảng ngày.
                reviewStartDate,
                // null nghĩa là không giới hạn cuối khoảng ngày.
                reviewEndDate,
                // Chuyển page 1-based của URL thành page 0-based, mỗi trang 5 review.
                PageRequest.of(normalizedReviewPage - 1, 5)
        );

        // Sau khi review bị ẩn/xóa, URL có thể trỏ tới trang lớn hơn số trang còn lại.
        if (reviewPageData.getTotalPages() > 0 && normalizedReviewPage > reviewPageData.getTotalPages()) {
            // Kéo số trang hiện tại về đúng trang cuối cùng còn dữ liệu.
            normalizedReviewPage = reviewPageData.getTotalPages();

            // Query lại bằng page index hợp lệ để giao diện không hiển thị một trang rỗng giả.
            reviewPageData = movieReviewService.getApprovedReviews(
                    id,
                    normalizedReviewRating,
                    reviewStartDate,
                    reviewEndDate,
                    PageRequest.of(normalizedReviewPage - 1, 5)
            );
        }

        // Đối tượng Movie là nguồn dữ liệu tiêu đề, poster, trailer và metadata của trang.
        model.addAttribute("movie", movie);

        // Template chỉ cần List nội dung, không cần toàn bộ Page object.
        model.addAttribute("reviews", reviewPageData.getContent());

        // Trả lại bộ lọc sao đã chuẩn hóa để select giữ lựa chọn.
        model.addAttribute("reviewRating", normalizedReviewRating);

        // Trả lại hai đầu ngày để input type=date giữ trạng thái.
        model.addAttribute("reviewStartDate", reviewStartDate);
        model.addAttribute("reviewEndDate", reviewEndDate);

        // Các giá trị phân trang dùng để bật/tắt và dựng link Trang trước/sau.
        model.addAttribute("reviewCurrentPage", normalizedReviewPage);
        model.addAttribute("reviewTotalPages", reviewPageData.getTotalPages());
        model.addAttribute("reviewFilteredCount", reviewPageData.getTotalElements());

        // Chỉ tính trung bình trên review APPROVED để nội dung bị admin ẩn không ảnh hưởng điểm.
        model.addAttribute("averageRating", movieReviewService.getAverageRating(id));

        // Đếm review APPROVED để hiển thị "(n đánh giá)".
        model.addAttribute("reviewCount", movieReviewService.getApprovedReviewCount(id));

        // Form review chỉ xuất hiện khi đã đăng nhập và có booking PAID với suất chiếu đã diễn ra.
        model.addAttribute("canReview", loggedInUser != null
                && movieReviewService.canReviewMovie(loggedInUser.getAccountID(), id));

        // Nếu người dùng từng review, đưa bản ghi cũ vào form để họ cập nhật thay vì tạo bản ghi trùng.
        model.addAttribute("userReview", loggedInUser == null
                ? null
                : movieReviewService.getUserReview(id, loggedInUser.getAccountID()).orElse(null));

        // Gợi ý 4 phim khác, đồng thời loại currentMovieId khỏi kết quả.
        model.addAttribute("recommendedMovies", movieRecommendationService.recommendMovies(
                loggedInUser == null ? null : loggedInUser.getAccountID(),
                id,
                4
        ));

        // Render templates/movie-detail.html.
        return "movie-detail";
    }

    @PostMapping("/movies/{id}/reviews")
    /**
     * Nhận đánh giá từ màn chi tiết phim.
     * Người chưa đăng nhập được ghi nhớ URL hiện tại để quay lại sau đăng nhập;
     * các điều kiện đã xem phim, số sao và độ dài bình luận do service kiểm tra.
     */
    public String submitMovieReview(@PathVariable("id") int id,
                                    @RequestParam("ratingScore") int ratingScore,
                                    @RequestParam(value = "comment", required = false) String comment,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        // Không tin dữ liệu account từ form; lấy Account đã đăng nhập trực tiếp từ server-side session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Khách vãng lai phải đăng nhập trước khi backend nhận review.
        if (loggedInUser == null) {
            // Lưu URL đích để LoginController có thể đưa người dùng quay lại đúng phim.
            session.setAttribute("redirectAfterLogin", "/movies/" + id);
            return "redirect:/login";
        }

        try {
            // Service kiểm tra lại rating, độ dài comment, phim, account và bằng chứng đã xem.
            movieReviewService.submitReview(id, loggedInUser.getAccountID(), ratingScore, comment);

            // Flash attribute chỉ sống qua một redirect và được hiển thị một lần ở trang chi tiết.
            redirectAttributes.addFlashAttribute("reviewSuccess", "Cảm ơn bạn đã gửi đánh giá.");
        } catch (IllegalArgumentException exception) {
            // Lỗi validation nghiệp vụ được trả lại cho người dùng thay vì tạo trang lỗi 500.
            redirectAttributes.addFlashAttribute("reviewError", exception.getMessage());
        }

        // Anchor #reviews đưa trình duyệt về đúng khu đánh giá sau khi redirect.
        return "redirect:/movies/" + id + "#reviews";
    }

    @GetMapping("/search")
    /**
     * Hiển thị màn kết quả tìm kiếm với nhiều bộ lọc, sắp xếp và phân trang.
     *
     * <p>Service trả toàn bộ kết quả đã lọc/sắp xếp; controller cắt trang 12 phim
     * và gửi lại các giá trị đang chọn để form Thymeleaf giữ nguyên trạng thái.</p>
     */
    public String searchMovies(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "genre", required = false) List<String> genres,
                               @RequestParam(value = "format", required = false) List<String> formats,
                               @RequestParam(value = "language", required = false) List<String> languages,
                               @RequestParam(value = "age", required = false) List<String> ageRatings,
                               @RequestParam(value = "status", required = false) String status,
                               @RequestParam(value = "sort", required = false, defaultValue = "featured") String sort,
                               @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                               HttpSession session,
                               Model model) {
        // Tài khoản được dùng cho header và thuật toán đề xuất; khách chưa login nhận null.
        Account loggedInUser = addLoggedInUser(session, model);

        // Service chuẩn hóa, lọc nhiều nhóm điều kiện và sắp xếp trước khi controller cắt trang.
        List<Movie> allMovies = movieService.searchMovies(keyword, genres, formats, languages, ageRatings, status, sort);

        // Mỗi trang kết quả hiển thị cố định tối đa 12 thẻ phim.
        int pageSize = 12;

        // Tổng số phần tử sau lọc dùng để tính số trang và hiển thị thống kê.
        int totalMovies = allMovies.size();

        // Luôn có ít nhất 1 trang về mặt UI, kể cả khi kết quả rỗng.
        int totalPages = Math.max(1, (int) Math.ceil((double) totalMovies / pageSize));

        // Chặn tham số page sửa tay vào khoảng hợp lệ 1..totalPages.
        int currentPage = Math.min(Math.max(page, 1), totalPages);

        // Chỉ số bắt đầu của subList; Math.min bảo vệ trường hợp danh sách rỗng.
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalMovies);

        // Chỉ số kết thúc không được vượt quá kích thước danh sách.
        int toIndex = Math.min(fromIndex + pageSize, totalMovies);

        // Chỉ truyền đúng lát dữ liệu của trang hiện tại sang Thymeleaf.
        model.addAttribute("movies", allMovies.subList(fromIndex, toIndex));

        // Các thuộc tính dưới đây phục vụ số liệu và link phân trang.
        model.addAttribute("totalMovies", totalMovies);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);

        // Trả lại keyword và toàn bộ lựa chọn để form không mất trạng thái sau request GET.
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedGenres", nullToEmpty(genres));
        model.addAttribute("selectedFormats", nullToEmpty(formats));
        model.addAttribute("selectedLanguages", nullToEmpty(languages));
        model.addAttribute("selectedAges", nullToEmpty(ageRatings));
        model.addAttribute("status", status);
        model.addAttribute("sort", sort);

        // Tải danh mục giá trị được phép để dựng các pill/dropdown lọc.
        model.addAttribute("genres", movieService.getActiveGenres());
        model.addAttribute("formats", movieService.getActiveFormats());
        model.addAttribute("languages", movieService.getActiveLanguages());
        model.addAttribute("ageRatings", movieService.getActiveAgeRatings());
        model.addAttribute("statuses", movieService.getMovieStatuses());

        // Khu đề xuất vẫn hiển thị độc lập với kết quả search.
        model.addAttribute("recommendedMovies", movieRecommendationService.recommendMovies(
                loggedInUser == null ? null : loggedInUser.getAccountID(),
                null,
                6
        ));

        // Render templates/search-result.html.
        return "search-result";
    }

    private Account addLoggedInUser(HttpSession session, Model model) {
        // Đọc Account từ session; frontend không thể tự truyền accountId để mạo danh.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Chỉ thêm vào model khi đã đăng nhập để Thymeleaf có thể phân biệt khách vãng lai.
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }

        // Trả Account cho method gọi dùng tiếp, tránh phải đọc session lần thứ hai.
        return loggedInUser;
    }

    /** Nhả ghế đang giữ (nếu có) khi user rời luồng đặt vé. */
    private void releaseHoldIfPresent(HttpSession session) {
        // Token liên kết các dòng BookingTicket HOLDING với đúng phiên trình duyệt.
        String holdToken = (String) session.getAttribute("seatHoldToken");

        // Không gọi lệnh DELETE nếu session chưa từng giữ ghế.
        if (holdToken != null && !holdToken.isBlank()) {
            try {
                // Xóa hold chưa gắn booking để trả ghế cho người dùng khác.
                seatHoldingService.releaseHold(holdToken);
            } catch (Exception ignored) {
                // Dọn hold thất bại không được chặn người dùng xem catalog phim.
            }

            // Dọn dữ liệu hiển thị bộ đếm của phiên cũ.
            session.removeAttribute("seatHoldToken");
            session.removeAttribute("seatHoldExpiresAt");
        }
    }

    private List<String> nullToEmpty(List<String> values) {
        // Thymeleaf xử lý empty list ổn định hơn null khi dùng contains/iteration.
        return values == null ? Collections.emptyList() : values;
    }
}
