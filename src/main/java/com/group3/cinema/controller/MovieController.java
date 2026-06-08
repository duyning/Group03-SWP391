package com.group3.cinema.controller;

/*
 * Added on 2026-06-04: Movie routes for listing, detail, and search.
 * Updated on 2026-06-04: Added GET /search for UC-G03 Search Movies.
 * Updated on 2026-06-04: Passed logged-in user to search page header.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.MovieService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MovieController {

    @Autowired
    private MovieService movieService;

    private void addLoggedInUser(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
    }

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
        addLoggedInUser(session, model);
        model.addAttribute("nowShowingMovies", movieService.getNowShowingMovies());
        model.addAttribute("comingSoonMovies", movieService.getComingSoonMovies());
        model.addAttribute("specialScreeningMovies", movieService.getSpecialScreeningMovies());
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
    public String showMovieDetail(@PathVariable("id") int id, HttpSession session, Model model) {
        Movie movie = movieService.getMovieDetail(id);
        if (movie == null) {
            return "redirect:/movies";
        }

        addLoggedInUser(session, model);
        model.addAttribute("movie", movie);
        return "movie-detail";
    }

    @GetMapping("/search")
    public String searchMovies(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "genre", required = false) String genre,
                               @RequestParam(value = "status", required = false) String status,
                               HttpSession session,
                               Model model) {
        addLoggedInUser(session, model);
        model.addAttribute("movies", movieService.searchMovies(keyword, genre, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("genre", genre);
        model.addAttribute("status", status);
        model.addAttribute("genres", movieService.getActiveGenres());
        model.addAttribute("statuses", movieService.getMovieStatuses());
        return "search-result";
    }
}
