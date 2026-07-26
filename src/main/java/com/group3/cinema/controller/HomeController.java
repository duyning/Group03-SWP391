package com.group3.cinema.controller;

/*
 * Updated on 2026-06-04: Added movie data for home page banners and sections.
 * Updated by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Banner;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.BannerService;
import com.group3.cinema.service.MovieService;
import com.group3.cinema.service.PostService;
import com.group3.cinema.service.SeatHoldingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Điều phối màn hình trang chủ.
 *
 * <p>Controller chỉ làm nhiệm vụ tập hợp dữ liệu cho giao diện: thông tin tài khoản
 * đang đăng nhập, banner đang hoạt động, danh sách phim nổi bật và bài viết mới.
 * Các quy tắc lọc/truy vấn dữ liệu được giao cho service tương ứng để controller
 * không chứa logic nghiệp vụ.</p>
 */
@Controller
public class HomeController {

    @Autowired
    // Spring inject MovieService để controller lấy danh sách phim hiển thị ngoài trang chủ.
    private MovieService movieService;

    @Autowired
    // Spring inject PostService để tải các bài viết/tin tức đã xuất bản gần nhất.
    private PostService postService;

    @Autowired
    // Spring inject BannerService để lấy đúng banner đang hoạt động tại vị trí HOME.
    private BannerService bannerService;

    @Autowired
    // Service này dùng để trả ghế nếu người dùng đang đặt vé nhưng quay về trang chủ.
    private SeatHoldingService seatHoldingService;

    /**
     * Hiển thị trang chủ tại cả URL gốc và {@code /home}.
     *
     * <p>Dữ liệu được đưa vào model theo đúng tên mà {@code home.html} sử dụng:</p>
     * <ul>
     *   <li>{@code user}: chỉ có khi session đã đăng nhập, dùng cho header chung.</li>
     *   <li>{@code homeBanners}: banner đúng vị trí HOME và còn hiệu lực.</li>
     *   <li>{@code hotMovies}: tối đa 5 phim có doanh thu tiền vé cao nhất trong 14 ngày gần nhất.</li>
     *   <li>{@code latestPosts}: các bài viết đã xuất bản gần nhất.</li>
     * </ul>
     */
    @GetMapping({"/", "/home"})
    public String showHome(HttpSession session, Model model) {
        // Đọc token giữ ghế do bước chọn ghế đã lưu trong HttpSession của trình duyệt hiện tại.
        String holdToken = (String) session.getAttribute("seatHoldToken");

        // Chỉ gọi database khi token thực sự tồn tại và không phải chuỗi rỗng.
        if (holdToken != null && !holdToken.isBlank()) {
            try {
                // Xóa các BookingTicket còn ở trạng thái HOLDING của token này để khách khác có thể chọn ghế.
                seatHoldingService.releaseHold(holdToken);
            } catch (Exception ignored) {
                // Việc dọn hold là tác vụ phụ; lỗi dọn ghế không được làm trang chủ không thể hiển thị.
            }

            // Xóa token khỏi session để request sau không cố gắng nhả lại cùng một nhóm ghế.
            session.removeAttribute("seatHoldToken");

            // Xóa luôn thời điểm hết hạn vì nó chỉ có ý nghĩa khi token giữ ghế còn tồn tại.
            session.removeAttribute("seatHoldExpiresAt");
        }

        // Lấy Account đã được LoginController lưu trong session sau khi đăng nhập thành công.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Khách chưa đăng nhập có loggedInUser = null nên không thêm thuộc tính user vào model.
        if (loggedInUser != null) {
            // common_header.html dùng thuộc tính user để hiển thị tên, menu tài khoản và thông báo.
            model.addAttribute("user", loggedInUser);
        }

        // Service lấy tối đa 5 phim có doanh thu tiền vé cao nhất trong 14 ngày gần nhất.
        List<Movie> hotMovies = movieService.getHotMovies();

        // Chỉ lấy banner đúng vị trí HOME và còn nằm trong thời gian được phép hiển thị.
        List<Banner> homeBanners = bannerService.getActiveBanners(Banner.BannerPage.HOME);

        // Tên "homeBanners" phải trùng biểu thức ${homeBanners} trong home.html.
        model.addAttribute("homeBanners", homeBanners);

        // Tên "hotMovies" được vòng lặp th:each trong home.html dùng để dựng các thẻ phim.
        model.addAttribute("hotMovies", hotMovies);

        // Lấy và truyền các bài viết đã publish để dựng khu vực "Tin tức mới".
        model.addAttribute("latestPosts", postService.getLatestPublishedPosts());

        // Trả logical view name; Thymeleaf sẽ tìm src/main/resources/templates/home.html.
        return "home";
    }
}
