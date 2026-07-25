package com.group3.cinema.controller;

/*
 * Controller class handling view routing and AJAX APIs for movie Wishlist.
 * Created by: Antigravity AI
 * Date: 2026-07-13
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.WishlistService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    /**
     * BƯỚC NGHỆP VỤ 1: Trang hiển thị Danh sách phim Yêu thích của Khách hàng.
     * Luồng gọi: Khách hàng click "Wishlist / Phim yêu thích" -> GET /wishlist -> WishlistController.viewWishlist(...)
     */
    @GetMapping("/wishlist")
    public String viewWishlist(HttpSession session, Model model) {
        // [1] Kiểm tra đăng nhập từ Session. Nếu chưa đăng nhập -> chuyển hướng về trang /login
        Account user = (Account) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        // [2] Gọi WishlistService tra cứu danh sách các đối tượng Movie nằm trong danh sách yêu thích của accountId
        List<Movie> wishlist = wishlistService.getWishlistMovies(user.getAccountID());
        model.addAttribute("user", user);
        model.addAttribute("wishlist", wishlist);
        model.addAttribute("active", "wishlist");
        return "wishlist";
    }

    /**
     * BƯỚC NGHỆP VỤ 2: REST API Toggle (Thêm / Xóa) Phim khỏi Danh sách Yêu thích.
     * Luồng gọi: Khách hàng click nút Trái tim trên trang Chi tiết phim (movie-detail.html)
     * -> JS gửi AJAX POST /api/wishlist/toggle?movieId=... -> WishlistController.toggleWishlist(...)
     */
    @PostMapping("/api/wishlist/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleWishlist(@RequestParam("movieId") int movieId, HttpSession session) {
        // [1] Kiểm tra trạng thái đăng nhập. Nếu chưa đăng nhập -> Trả về HTTP 401 Unauthorized
        Account user = (Account) session.getAttribute("loggedInUser");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập trước khi thêm phim vào danh sách yêu thích."));
        }
        try {
            // [2] Gọi WishlistService đảo trạng thái: Nếu chưa thích -> Thêm mới & trả về added = true, nếu đã thích -> Xóa & trả về added = false
            boolean added = wishlistService.toggleWishlist(user, movieId);
            return ResponseEntity.ok(Map.of("added", added, "message", added ? "Đã thêm vào danh sách yêu thích!" : "Đã xóa khỏi danh sách yêu thích."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * BƯỚC NGHỆP VỤ 3: REST API Kiểm tra trạng thái phim này đã nằm trong Wishlist hay chưa.
     * Luồng gọi: Trang movie-detail.html khi tải xong -> JS gửi AJAX GET /api/wishlist/check?movieId=...
     * -> Nếu wishlisted = true -> Đổi biểu tượng Trái tim thành màu đỏ (active).
     */
    @GetMapping("/api/wishlist/check")
    @ResponseBody
    public ResponseEntity<?> checkWishlist(@RequestParam("movieId") int movieId, HttpSession session) {
        Account user = (Account) session.getAttribute("loggedInUser");
        if (user == null) {
            return ResponseEntity.ok(Map.of("wishlisted", false));
        }
        boolean isWishlisted = wishlistService.isWishlisted(user.getAccountID(), movieId);
        return ResponseEntity.ok(Map.of("wishlisted", isWishlisted));
    }
}
