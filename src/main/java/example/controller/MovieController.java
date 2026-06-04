package example.controller;

/*
 * Added on 2026-06-04: Movie routes for listing, detail, and search.
 * Created by: HuyPB - HE191335
 */

import example.model.Movie;
import example.service.MovieService;
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

    /**
     * GET /movies
     *
     * Business flow:
     * - Customer opens the movie listing page.
     * - Controller asks MovieService for active movies grouped by status.
     * - Thymeleaf renders each group in its own section.
     */
    @GetMapping("/movies")
    public String showMovies(Model model) {
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
    public String showMovieDetail(@PathVariable("id") int id, Model model) {
        Movie movie = movieService.getMovieDetail(id);
        if (movie == null) {
            return "redirect:/movies";
        }

        model.addAttribute("movie", movie);
        return "movie-detail";
    }

    /**
     * GET /movies/search?keyword=...
     *
     * Business flow:
     * - Customer submits the search form from home/list/detail pages.
     * - Controller keeps the keyword in the model so the input can show it back.
     * - Search results are passed as "movies"; movie-list.html switches to
     *   result mode when this attribute exists.
     */
    @GetMapping("/movies/search")
    public String searchMovies(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("movies", movieService.searchMovies(keyword));
        return "movie-list";
    }
}
