package com.group3.cinema.controller;

/*
 * Created on 2026-06-09: Customer-facing news pages.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Banner;
import com.group3.cinema.entity.Post;
import com.group3.cinema.service.BannerService;
import com.group3.cinema.service.PostService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class CustomerPostController {

    private final PostService postService;
    private final BannerService bannerService;

    public CustomerPostController(PostService postService, BannerService bannerService) {
        this.postService = postService;
        this.bannerService = bannerService;
    }

    @GetMapping({"/posts", "/news"})
    public String showNews(HttpSession session, Model model) {
        addLoggedInUser(session, model);
        model.addAttribute("posts", postService.getPublishedPosts());
        Banner newsBanner = bannerService.getActiveNewsBanner();
        model.addAttribute("newsBanner", newsBanner);
        return "customer-post-list";
    }

    @GetMapping({"/posts/{id}", "/news/{id}"})
    public String showNewsDetail(@PathVariable("id") Long id, HttpSession session, Model model) {
        Post post = postService.getPublishedPost(id);
        addLoggedInUser(session, model);
        model.addAttribute("post", post);
        model.addAttribute("latestPosts", postService.getLatestPublishedPosts());
        return "customer-post-detail";
    }

    private void addLoggedInUser(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
    }
}
