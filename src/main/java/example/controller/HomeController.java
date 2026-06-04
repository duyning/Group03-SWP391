package example.controller;

/*
 * Updated on 2026-06-04: Added movie data for home page banners and sections.
 * Updated by: HuyPB - HE191335
 */

import example.entity.Account;
import example.service.MovieService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private MovieService movieService;

    /**
     * Show home page.
     *
     * Business flow:
     * - Load logged-in user from session when available, so the header can show
     *   account actions and user information.
     * - Load active movies for homepage presentation:
     *   bannerMovies for the hero area, then movie groups by status.
     * - Return home.html for Thymeleaf rendering.
     */
    @GetMapping({"/", "/home"})
    public String showHome(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
        model.addAttribute("bannerMovies", movieService.getRandomBannerMovies());
        model.addAttribute("nowShowingMovies", movieService.getNowShowingMovies());
        model.addAttribute("comingSoonMovies", movieService.getComingSoonMovies());
        model.addAttribute("specialScreeningMovies", movieService.getSpecialScreeningMovies());
        return "home";
    }
}
