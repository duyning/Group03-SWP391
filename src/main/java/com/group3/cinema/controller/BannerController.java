package com.group3.cinema.controller;

/*
 * Created on 2026-06-09: Admin controller for homepage and news banner management.
 * Updated on 2026-06-25: Display linked movie names instead of raw movie URLs.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Banner;
import com.group3.cinema.entity.Movie;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Banner banner = new Banner();
        banner.setPage(page);
        populateBannerPage(model, banner, page, null);
        return "banner-list";
    }

    @GetMapping("/edit/{id}")
    public String editBanner(@PathVariable("id") Long id, Model model) {
        Banner banner = bannerService.getBanner(id);
        populateBannerPage(model, banner, banner.getPage(), extractMovieId(banner.getLinkUrl()));
        return "banner-list";
    }

    @PostMapping("/save")
    public String saveBanner(@ModelAttribute Banner banner,
                             @RequestParam(value = "movieId", required = false) Integer movieId,
                             @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        try {
            banner.setLinkUrl(resolveMovieLink(movieId));
            bannerService.saveBanner(banner, imageFile);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu banner thành công.");
            return "redirect:/admin/banners?page=" + banner.getPage().name();
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateBannerPage(model, banner, banner.getPage(), movieId);
            return "banner-list";
        }
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
        if (linkUrl == null || !linkUrl.contains("/movies/")) {
            return null;
        }
        try {
            return Integer.valueOf(linkUrl.substring(linkUrl.lastIndexOf("/movies/") + "/movies/".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveMovieLink(Integer movieId) {
        if (movieId == null) {
            return null;
        }
        Movie movie = movieRepository.findByIdAndActiveTrue(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Phim điều hướng không tồn tại hoặc đang bị ẩn."));
        return "/movies/" + movie.getId();
    }

    private void populateBannerPage(Model model,
                                    Banner banner,
                                    Banner.BannerPage selectedPage,
                                    Integer selectedMovieId) {
        List<Banner> banners = bannerService.getBanners(selectedPage);
        model.addAttribute("banners", banners);
        model.addAttribute("banner", banner);
        model.addAttribute("pages", Banner.BannerPage.values());
        model.addAttribute("movies", movieRepository.findByActiveTrue());
        model.addAttribute("movieLinkLabels", buildMovieLinkLabels());
        model.addAttribute("selectedPage", selectedPage);
        model.addAttribute("selectedMovieId", selectedMovieId);
        addBannerMetrics(banners, model);
    }

    private Map<String, String> buildMovieLinkLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        movieRepository.findAll().forEach(movie -> {
            String relativeLink = "/movies/" + movie.getId();
            labels.put(relativeLink, movie.getTitle());
            labels.put("http://localhost:8080" + relativeLink, movie.getTitle());
            labels.put("https://localhost:8080" + relativeLink, movie.getTitle());
        });
        return labels;
    }

    private void addBannerMetrics(List<Banner> banners, Model model) {
        long activeCount = banners.stream().filter(Banner::isActive).count();
        model.addAttribute("totalBanners", banners.size());
        model.addAttribute("activeBanners", activeCount);
        model.addAttribute("hiddenBanners", banners.size() - activeCount);
    }
}
