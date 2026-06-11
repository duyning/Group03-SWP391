package com.group3.cinema.controller;

/*
 * Created on 2026-06-09: Added admin dashboard route for manager/admin login flow.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardController {

    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;

    public AdminDashboardController(MovieRepository movieRepository,
                                    RoomRepository roomRepository,
                                    SeatRepository seatRepository,
                                    ShowtimeRepository showtimeRepository) {
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
    }

    @GetMapping("/admin/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }

        model.addAttribute("movieCount", movieRepository.count());
        model.addAttribute("roomCount", roomRepository.count());
        model.addAttribute("seatCount", seatRepository.count());
        model.addAttribute("showtimeCount", showtimeRepository.count());
        model.addAttribute("active", "dashboard");
        return "admin_dashboard";
    }
}
