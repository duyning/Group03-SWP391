package com.group3.cinema.controller;

/*
 * Created on 2026-06-09: Admin controller for homepage and news banner management.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Banner;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.service.BannerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/admin/banners")
public class BannerController {

    private final BannerService bannerService;
    private final MovieRepository movieRepository;

    public BannerController(BannerService bannerService, MovieRepository movieRepository) {
        this.bannerService = bannerService;
        this.movieRepository = movieRepository;
    }

    @GetMapping
    public String listBanners(@RequestParam(value = "page", required = false) Banner.BannerPage page,
                              Model model) {
        model.addAttribute("banners", bannerService.getBanners(page));
        model.addAttribute("banner", new Banner());
        model.addAttribute("pages", Banner.BannerPage.values());
        model.addAttribute("movies", movieRepository.findByActiveTrue());
        model.addAttribute("selectedPage", page);
        model.addAttribute("selectedMovieId", null);
        return "banner-list";
    }

    @GetMapping("/edit/{id}")
    public String editBanner(@PathVariable("id") Long id, Model model) {
        Banner banner = bannerService.getBanner(id);
        model.addAttribute("banners", bannerService.getBanners(banner.getPage()));
        model.addAttribute("banner", banner);
        model.addAttribute("pages", Banner.BannerPage.values());
        model.addAttribute("movies", movieRepository.findByActiveTrue());
        model.addAttribute("selectedPage", banner.getPage());
        model.addAttribute("selectedMovieId", extractMovieId(banner.getLinkUrl()));
        return "banner-list";
    }

    @PostMapping("/save")
    public String saveBanner(@ModelAttribute Banner banner,
                             @RequestParam(value = "movieId", required = false) Integer movieId,
                             @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                             RedirectAttributes redirectAttributes) throws IOException {
        if (movieId != null) {
            banner.setLinkUrl("/movies/" + movieId);
        } else {
            banner.setLinkUrl(null);
        }
        bannerService.saveBanner(banner, imageFile);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu banner thành công.");
        return "redirect:/admin/banners?page=" + banner.getPage().name();
    }

    @GetMapping("/delete/{id}")
    public String deleteBanner(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Banner banner = bannerService.getBanner(id);
        Banner.BannerPage page = banner.getPage();
        bannerService.deleteBanner(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa banner.");
        return "redirect:/admin/banners?page=" + page.name();
    }

    private Integer extractMovieId(String linkUrl) {
        if (linkUrl == null || !linkUrl.startsWith("/movies/")) {
            return null;
        }
        try {
            return Integer.valueOf(linkUrl.substring("/movies/".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
