package org.example.group03.controller;
import lombok.RequiredArgsConstructor;
import org.example.group03.entity.Post;
import org.example.group03.repository.PostRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Controller
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostRepository postRepository;

    @GetMapping
    public String listPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            Model model) {

        // Trim khoảng trắng nếu admin cố tình nhập toàn space
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        // Gọi câu lệnh tìm kiếm linh hoạt từ Repository
        List<Post> filteredPosts = postRepository.searchPosts(searchKeyword, category, status);

        model.addAttribute("posts", filteredPosts);

        // Trả về đúng tên file view danh sách của cậu
        return "post-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("post", new Post());
        return "create-post";
    }

    @PostMapping("/save")
    public String savePost(
            @ModelAttribute Post post,
            @RequestParam("thumbnailFile") MultipartFile file)
            throws IOException {

        if (!file.isEmpty()) {

            String fileName =
                    System.currentTimeMillis() + "_"
                            + file.getOriginalFilename();

            Path uploadPath = Paths.get("uploads");

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Files.copy(
                    file.getInputStream(),
                    uploadPath.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING
            );

            post.setThumbnail("/uploads/" + fileName);
        }

        postRepository.save(post);

        return "redirect:/admin/posts";
    }
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết"));

        model.addAttribute("post", post);

        return "post-edit";
    }

    @PostMapping("/update")
    public String updatePost(@ModelAttribute Post post) {
        postRepository.save(post);
        return "redirect:/admin/posts";
    }

    @GetMapping("/delete/{id}")
    public String deletePost(@PathVariable Long id) {
        postRepository.deleteById(id);
        return "redirect:/admin/posts";
    }

    @GetMapping("/{id}")
    public String viewPost(@PathVariable Long id, Model model) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết"));

        model.addAttribute("post", post);

        return "post-detail";
    }
}