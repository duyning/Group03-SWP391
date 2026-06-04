package com.group3.cinema.controller;

/*
 * Updated on 2026-06-04: Added movie data for home page banners and sections.
 * Updated by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.MovieService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controller xá»­ lÃ½ hiá»ƒn thá»‹ trang chá»§ (Home Page).
 * ÄÄƒng nháº­p thÃ nh cÃ´ng sáº½ hiá»ƒn thá»‹ giao diá»‡n trang chá»§ vá»›i thÃ´ng tin ngÆ°á»i dÃ¹ng.
 *
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
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
     * - Load about 5 active movies as hotMovies for homepage presentation.
     * - Reuse hotMovies as bannerMovies so the home page stays focused.
     * - Customers who want the full movie catalog are sent to GET /movies.
     * - Return home.html for Thymeleaf rendering.
     */
    @GetMapping({"/", "/home"})
    public String showHome(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
        List<Movie> hotMovies = movieService.getRandomBannerMovies();
        model.addAttribute("bannerMovies", hotMovies);
        model.addAttribute("hotMovies", hotMovies);
        return "home";
    }
}
