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
    private MovieService movieService;

    @Autowired
    private PostService postService;

    @Autowired
    private BannerService bannerService;

    @Autowired
    private SeatHoldingService seatHoldingService;

    /**
     * Hiển thị trang chủ tại cả URL gốc và {@code /home}.
     *
     * <p>Dữ liệu được đưa vào model theo đúng tên mà {@code home.html} sử dụng:</p>
     * <ul>
     *   <li>{@code user}: chỉ có khi session đã đăng nhập, dùng cho header chung.</li>
     *   <li>{@code homeBanners}: banner đúng vị trí HOME và còn hiệu lực.</li>
     *   <li>{@code hotMovies}: tối đa 5 phim hoạt động, dùng cho khu vực phim nổi bật.</li>
     *   <li>{@code latestPosts}: các bài viết đã xuất bản gần nhất.</li>
     * </ul>
     */
    @GetMapping({"/", "/home"})
    public String showHome(HttpSession session, Model model) {
        // Nhả ghế đang giữ (nếu có) khi user quay về trang chủ
        String holdToken = (String) session.getAttribute("seatHoldToken");
        if (holdToken != null && !holdToken.isBlank()) {
            try { seatHoldingService.releaseHold(holdToken); } catch (Exception ignored) {}
            session.removeAttribute("seatHoldToken");
            session.removeAttribute("seatHoldExpiresAt");
        }
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
        List<Movie> hotMovies = movieService.getRandomBannerMovies();
        List<Banner> homeBanners = bannerService.getActiveBanners(Banner.BannerPage.HOME);
        model.addAttribute("homeBanners", homeBanners);
        model.addAttribute("hotMovies", hotMovies);
        model.addAttribute("latestPosts", postService.getLatestPublishedPosts());
        return "home";
    }
}
