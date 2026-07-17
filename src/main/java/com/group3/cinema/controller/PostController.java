package com.group3.cinema.controller;

import com.group3.cinema.entity.Post;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.service.CustomerNotificationBroadcastService;
import com.group3.cinema.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
@RequestMapping("/admin/posts")
public class PostController {

    private final PostService postService;
    private final CustomerNotificationBroadcastService notificationBroadcastService;

    public PostController(PostService postService,
                          CustomerNotificationBroadcastService notificationBroadcastService) {
        this.postService = postService;
        this.notificationBroadcastService = notificationBroadcastService;
    }

    @GetMapping
    public String listPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            Model model) {
        model.addAttribute("posts", postService.searchPosts(keyword, category, status));
        return "post-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("post", new Post());
        return "create-post";
    }

    @PostMapping("/save")
    public String savePost(
            @ModelAttribute("post") Post post,
            BindingResult bindingResult,
            @RequestParam("thumbnailFile") MultipartFile file,
            RedirectAttributes redirectAttributes) throws IOException {
        if (postService.existsByTitle(post.getTitle())) {
            bindingResult.rejectValue("title", "error.post", "Tiêu đề bài viết này đã tồn tại trong hệ thống.");
        }
        if (bindingResult.hasErrors()) {
            return "create-post";
        }

        Post savedPost = postService.createPost(post, file);
        if ("PUBLISHED".equalsIgnoreCase(savedPost.getStatus())) {
            notificationBroadcastService.sendToActiveCustomers(
                    "Tin tức mới: " + savedPost.getTitle(),
                    savedPost.getSummary(),
                    NotificationType.NEWS,
                    savedPost.getThumbnail(),
                    "/posts/" + savedPost.getId()
            );
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm bài viết mới.");
        return "redirect:/admin/posts";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", postService.getPost(id));
        return "post-edit";
    }

    @PostMapping("/update")
    public String updatePost(
            @ModelAttribute("post") Post post,
            BindingResult bindingResult,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) throws IOException {
        if (postService.existsByTitleAndIdNot(post.getTitle(), post.getId())) {
            bindingResult.rejectValue("title", "error.post", "Tiêu đề này đã bị trùng với một bài viết khác.");
        }
        if (bindingResult.hasErrors()) {
            Post oldPost = postService.getPost(post.getId());
            post.setThumbnail(oldPost.getThumbnail());
            return "post-edit";
        }

        postService.updatePost(post, file);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật bài viết.");
        return "redirect:/admin/posts";
    }

    @GetMapping("/delete/{id}")
    public String deletePost(@PathVariable("id") Long id,
                             RedirectAttributes redirectAttributes) {
        postService.deletePost(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa bài viết.");
        return "redirect:/admin/posts";
    }

    @GetMapping("/{id}")
    public String viewPost(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", postService.getPost(id));
        return "post-detail";
    }
}
