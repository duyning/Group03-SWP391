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

/**
 * Controller quản lý phân hệ Bài viết / Tin tức (Post Management).
 * Phân quyền dành cho Quản trị viên (Admin Dashboard).
 *
 * @author Group 3 - Cinema Management System
 */
@Controller
@RequestMapping("/admin/posts")
public class PostController {

    private final PostService postService;
    private final CustomerNotificationBroadcastService notificationBroadcastService;

    // Tiêm phụ thuộc (Dependency Injection) qua Constructor
    public PostController(PostService postService,
                          CustomerNotificationBroadcastService notificationBroadcastService) {
        this.postService = postService;
        this.notificationBroadcastService = notificationBroadcastService;
    }

    /**
     * Hiển thị danh sách bài viết trong trang quản trị.
     * Hỗ trợ tìm kiếm và lọc đa điều kiện theo từ khóa, danh mục và trạng thái.
     */
    @GetMapping
    public String listPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            Model model) {
        // Nạp danh sách bài viết đã lọc sang Model để Thymeleaf render
        model.addAttribute("posts", postService.searchPosts(keyword, category, status));
        return "post-list";
    }

    /**
     * Hiển thị giao diện form tạo bài viết mới.
     */
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("post", new Post());
        return "create-post";
    }

    /**
     * Xử lý lưu bài viết mới vào CSDL và tự động phát thông báo Broadcast tới khách hàng nếu bài viết được Xuất bản.
     */
    @PostMapping("/save")
    public String savePost(
            @ModelAttribute("post") Post post,
            BindingResult bindingResult,
            @RequestParam("thumbnailFile") MultipartFile file,
            RedirectAttributes redirectAttributes) throws IOException {
        // Kiểm tra validation: Xem tiêu đề bài viết đã tồn tại trong hệ thống chưa
        if (postService.existsByTitle(post.getTitle())) {
            bindingResult.rejectValue("title", "error.post", "Tiêu đề bài viết này đã tồn tại trong hệ thống.");
        }
        if (bindingResult.hasErrors()) {
            return "create-post";
        }

        // Thực thi tạo bài viết và lưu ảnh thumbnail
        Post savedPost = postService.createPost(post, file);

        // Nghiệp vụ: Nếu bài viết ở trạng thái PUBLISHED, tự động phát thông báo tới tất cả tài khoản Khách hàng
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

    /**
     * Hiển thị giao diện chỉnh sửa bài viết theo ID.
     */
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", postService.getPost(id));
        return "post-edit";
    }

    /**
     * Xử lý cập nhật thông tin bài viết đã tồn tại.
     */
    @PostMapping("/update")
    public String updatePost(
            @ModelAttribute("post") Post post,
            BindingResult bindingResult,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) throws IOException {
        // Kiểm tra xem tiêu đề cập nhật có bị trùng với một bài viết khác không (trừ chính nó)
        if (postService.existsByTitleAndIdNot(post.getTitle(), post.getId())) {
            bindingResult.rejectValue("title", "error.post", "Tiêu đề này đã bị trùng với một bài viết khác.");
        }
        if (bindingResult.hasErrors()) {
            // Nếu có lỗi, giữ lại đường dẫn ảnh thumbnail cũ để không làm hỏng hiển thị giao diện
            Post oldPost = postService.getPost(post.getId());
            post.setThumbnail(oldPost.getThumbnail());
            return "post-edit";
        }

        postService.updatePost(post, file);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật bài viết.");
        return "redirect:/admin/posts";
    }

    /**
     * Xử lý xóa bài viết khỏi CSDL theo ID.
     */
    @GetMapping("/delete/{id}")
    public String deletePost(@PathVariable("id") Long id,
                             RedirectAttributes redirectAttributes) {
        postService.deletePost(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa bài viết.");
        return "redirect:/admin/posts";
    }

    /**
     * Xem chi tiết thông tin bài viết theo ID.
     */
    @GetMapping("/{id}")
    public String viewPost(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", postService.getPost(id));
        return "post-detail";
    }
}