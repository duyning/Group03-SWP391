package com.group3.cinema.controller;

/*
 * Added on 2026-06-04: Movie routes for listing, detail, and search.
 * Updated on 2026-06-04: Added GET /search for UC-G03 Search Movies.
 * Updated on 2026-06-04: Passed logged-in user to search page header.
 * Updated on 2026-06-26: Added multi-select guest search filters, sorting,
 * and pagination model data for search-result.html.
 * Created by: HuyPB - HE191335
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
    private MovieService movieService;

    @Autowired
    private MovieReviewService movieReviewService;

    @Autowired
    private MovieRecommendationService movieRecommendationService;

    @Autowired
    private SeatHoldingService seatHoldingService;

    /**
     * GET /movies
     *
     * Business flow:
     * - Customer opens the movie listing page.
     * - Controller asks MovieService for active movies grouped by status.
     * - Thymeleaf renders each group in its own section.
     */
    @GetMapping("/movies")
    public String showMovies(HttpSession session, Model model) {
        releaseHoldIfPresent(session);
        Account loggedInUser = addLoggedInUser(session, model);
        model.addAttribute("nowShowingMovies", movieService.getNowShowingMovies());
        model.addAttribute("comingSoonMovies", movieService.getComingSoonMovies());
        model.addAttribute("specialScreeningMovies", movieService.getSpecialScreeningMovies());
        model.addAttribute("recommendedMovies", movieRecommendationService.recommendMovies(
                loggedInUser == null ? null : loggedInUser.getAccountID(),
                null,
                6
        ));
        return "movie-list";
    }

    /**
     * GET /movies/{id}
     *
     * Business flow:
     * - Customer clicks a movie card.
     * - Controller loads only active movie detail by id.
     * - If the movie is missing or inactive, redirect to the listing page.
     * - If found, pass the movie object to movie-detail.html.
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
        Movie movie = movieService.getMovieDetail(id);
        if (movie == null) {
            return "redirect:/movies";
        }

        Account loggedInUser = addLoggedInUser(session, model);
        int normalizedReviewPage = Math.max(reviewPage, 1);
        Integer normalizedReviewRating = reviewRating != null && reviewRating >= 1 && reviewRating <= 5
                ? reviewRating
                : null;
        if (reviewStartDate != null && reviewEndDate != null && reviewStartDate.isAfter(reviewEndDate)) {
            LocalDate originalStartDate = reviewStartDate;
            reviewStartDate = reviewEndDate;
            reviewEndDate = originalStartDate;
        }
        Page<MovieReview> reviewPageData = movieReviewService.getApprovedReviews(
                id,
                normalizedReviewRating,
                reviewStartDate,
                reviewEndDate,
                PageRequest.of(normalizedReviewPage - 1, 5)
        );
        if (reviewPageData.getTotalPages() > 0 && normalizedReviewPage > reviewPageData.getTotalPages()) {
            normalizedReviewPage = reviewPageData.getTotalPages();
            reviewPageData = movieReviewService.getApprovedReviews(
                    id,
                    normalizedReviewRating,
                    reviewStartDate,
                    reviewEndDate,
                    PageRequest.of(normalizedReviewPage - 1, 5)
            );
        }

        model.addAttribute("movie", movie);
        model.addAttribute("reviews", reviewPageData.getContent());
        model.addAttribute("reviewRating", normalizedReviewRating);
        model.addAttribute("reviewStartDate", reviewStartDate);
        model.addAttribute("reviewEndDate", reviewEndDate);
        model.addAttribute("reviewCurrentPage", normalizedReviewPage);
        model.addAttribute("reviewTotalPages", reviewPageData.getTotalPages());
        model.addAttribute("reviewFilteredCount", reviewPageData.getTotalElements());
        model.addAttribute("averageRating", movieReviewService.getAverageRating(id));
        model.addAttribute("reviewCount", movieReviewService.getApprovedReviewCount(id));
        model.addAttribute("canReview", loggedInUser != null
                && movieReviewService.canReviewMovie(loggedInUser.getAccountID(), id));
        model.addAttribute("userReview", loggedInUser == null
                ? null
                : movieReviewService.getUserReview(id, loggedInUser.getAccountID()).orElse(null));
        model.addAttribute("recommendedMovies", movieRecommendationService.recommendMovies(
                loggedInUser == null ? null : loggedInUser.getAccountID(),
                id,
                4
        ));
        return "movie-detail";
    }

    @PostMapping("/movies/{id}/reviews")
    public String submitMovieReview(@PathVariable("id") int id,
                                    @RequestParam("ratingScore") int ratingScore,
                                    @RequestParam(value = "comment", required = false) String comment,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            session.setAttribute("redirectAfterLogin", "/movies/" + id);
            return "redirect:/login";
        }

        try {
            movieReviewService.submitReview(id, loggedInUser.getAccountID(), ratingScore, comment);
            redirectAttributes.addFlashAttribute("reviewSuccess", "Cảm ơn bạn đã gửi đánh giá.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("reviewError", exception.getMessage());
        }
        return "redirect:/movies/" + id + "#reviews";
    }

    @GetMapping("/search")
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
        Account loggedInUser = addLoggedInUser(session, model);

        List<Movie> allMovies = movieService.searchMovies(keyword, genres, formats, languages, ageRatings, status, sort);
        int pageSize = 12;
        int totalMovies = allMovies.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalMovies / pageSize));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalMovies);
        int toIndex = Math.min(fromIndex + pageSize, totalMovies);

        model.addAttribute("movies", allMovies.subList(fromIndex, toIndex));
        model.addAttribute("totalMovies", totalMovies);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedGenres", nullToEmpty(genres));
        model.addAttribute("selectedFormats", nullToEmpty(formats));
        model.addAttribute("selectedLanguages", nullToEmpty(languages));
        model.addAttribute("selectedAges", nullToEmpty(ageRatings));
        model.addAttribute("status", status);
        model.addAttribute("sort", sort);
        model.addAttribute("genres", movieService.getActiveGenres());
        model.addAttribute("formats", movieService.getActiveFormats());
        model.addAttribute("languages", movieService.getActiveLanguages());
        model.addAttribute("ageRatings", movieService.getActiveAgeRatings());
        model.addAttribute("statuses", movieService.getMovieStatuses());
        model.addAttribute("recommendedMovies", movieRecommendationService.recommendMovies(
                loggedInUser == null ? null : loggedInUser.getAccountID(),
                null,
                6
        ));
        return "search-result";
    }

    private Account addLoggedInUser(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
        return loggedInUser;
    }

    /** Nhả ghế đang giữ (nếu có) khi user rời luồng đặt vé. */
    private void releaseHoldIfPresent(HttpSession session) {
        String holdToken = (String) session.getAttribute("seatHoldToken");
        if (holdToken != null && !holdToken.isBlank()) {
            try { seatHoldingService.releaseHold(holdToken); } catch (Exception ignored) {}
            session.removeAttribute("seatHoldToken");
            session.removeAttribute("seatHoldExpiresAt");
        }
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
