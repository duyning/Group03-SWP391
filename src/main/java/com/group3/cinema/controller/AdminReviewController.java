package com.group3.cinema.controller;

/*
 * Added on 2026-07-10: Admin movie review management screen and visibility controls.
 * This controller supports filtering, paging, and hiding/showing customer movie reviews.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MovieReview;
import com.group3.cinema.service.MovieReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {

    private final MovieReviewService movieReviewService;

    public AdminReviewController(MovieReviewService movieReviewService) {
        this.movieReviewService = movieReviewService;
    }

    @GetMapping
    public String listReviews(@RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
                              @RequestParam(value = "startDate", required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                              @RequestParam(value = "endDate", required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                              @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                              @RequestParam(value = "size", required = false, defaultValue = "10") int size,
                              HttpSession session,
                              Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        List<MovieReview> filteredReviews = movieReviewService.searchReviewsForAdmin(keyword, status, startDate, endDate);

        // Keep paging in-memory because admin filters combine keyword, status, and date range.
        int pageSize = Math.min(Math.max(size, 5), 50);
        int totalReviews = filteredReviews.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalReviews / pageSize));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalReviews);
        int toIndex = Math.min(fromIndex + pageSize, totalReviews);
        long allReviewCount = movieReviewService.getTotalReviewCount();
        long visibleReviewCount = movieReviewService.getVisibleReviewCount();

        model.addAttribute("user", loggedInUser);
        model.addAttribute("reviews", filteredReviews.subList(fromIndex, toIndex));
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("allReviewCount", allReviewCount);
        model.addAttribute("visibleReviewCount", visibleReviewCount);
        model.addAttribute("hiddenReviewCount", allReviewCount - visibleReviewCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin-review-list";
    }

    @PostMapping("/{id}/visibility")
    public String updateVisibility(@PathVariable("id") Long id,
                                   @RequestParam("visible") boolean visible,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        try {
            // Visibility is stored as moderation status so reviews can be restored later.
            movieReviewService.setReviewVisible(id, loggedInUser.getAccountID(), visible);
            redirectAttributes.addFlashAttribute("successMessage",
                    visible ? "Đã hiển thị lại đánh giá." : "Đã ẩn đánh giá.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/reviews";
    }
}
