package com.group3.cinema.controller;

import com.group3.cinema.entity.Post;
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

import java.io.IOException;

@Controller
@RequestMapping("/admin/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public String listPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            Model model) {
        model.addAttribute("posts", postService.searchPosts(keyword, category, status));
        return "post-list"; // ĐÚNG chuẩn tên file của cậu
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("post", new Post());
        return "create-post"; // ĐÚNG chuẩn tên file của cậu
    }

    @PostMapping("/save")
    public String savePost(
            @ModelAttribute("post") Post post,
            BindingResult bindingResult,
            @RequestParam("thumbnailFile") MultipartFile file,
            Model model) throws IOException {

        // 1. Kiểm tra trùng tiêu đề bài viết khi THÊM MỚI
        if (postService.existsByTitle(post.getTitle())) {
            bindingResult.rejectValue("title", "error.post", "Tiêu đề bài viết này đã tồn tại trong hệ thống!");
        }

        // 2. Nếu có lỗi trùng tiêu đề, trả về trang create-post của cậu
        if (bindingResult.hasErrors()) {
            return "create-post"; // ĐÚNG chuẩn tên file của cậu
        }

        postService.createPost(post, file);
        return "redirect:/admin/posts";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", postService.getPost(id));
        return "post-edit"; // ĐÚNG chuẩn tên file của cậu
    }

    @PostMapping("/update")
    public String updatePost(
            @ModelAttribute("post") Post post,
            BindingResult bindingResult,
            @RequestParam("thumbnailFile") MultipartFile file,
            Model model) throws IOException {

        // 3. Kiểm tra trùng tiêu đề bài viết khi CẬP NHẬT (Trừ chính bài đang sửa ra)
        if (postService.existsByTitleAndIdNot(post.getTitle(), post.getId())) {
            bindingResult.rejectValue("title", "error.post", "Tiêu đề này đã bị trùng với một bài viết khác!");
        }

        // 4. Nếu có lỗi trùng tiêu đề, quay lại trang post-edit của cậu và giữ lại ảnh cũ
        if (bindingResult.hasErrors()) {
            Post oldPost = postService.getPost(post.getId());
            post.setThumbnail(oldPost.getThumbnail());
            return "post-edit"; // ĐÚNG chuẩn tên file của cậu
        }

        postService.updatePost(post, file);
        return "redirect:/admin/posts";
    }

    @GetMapping("/delete/{id}")
    public String deletePost(@PathVariable("id") Long id) {
        postService.deletePost(id);
        return "redirect:/admin/posts";
    }

    @GetMapping("/{id}")
    public String viewPost(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", postService.getPost(id));
        return "post-detail";
    }
}