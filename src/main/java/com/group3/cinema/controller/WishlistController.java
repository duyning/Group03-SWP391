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

    @GetMapping("/wishlist")
    public String viewWishlist(HttpSession session, Model model) {
        Account user = (Account) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        List<Movie> wishlist = wishlistService.getWishlistMovies(user.getAccountID());
        model.addAttribute("user", user);
        model.addAttribute("wishlist", wishlist);
        model.addAttribute("active", "wishlist");
        return "wishlist";
    }

    @PostMapping("/api/wishlist/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleWishlist(@RequestParam("movieId") int movieId, HttpSession session) {
        Account user = (Account) session.getAttribute("loggedInUser");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập trước khi thêm phim vào danh sách yêu thích."));
        }
        try {
            boolean added = wishlistService.toggleWishlist(user, movieId);
            return ResponseEntity.ok(Map.of("added", added, "message", added ? "Đã thêm vào danh sách yêu thích!" : "Đã xóa khỏi danh sách yêu thích."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

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
