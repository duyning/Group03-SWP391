package com.group3.cinema.service;

import com.group3.cinema.entity.Post;
import com.group3.cinema.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class PostService {

    private static final Path UPLOAD_PATH = Paths.get("uploads");

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public List<Post> searchPosts(String keyword, String category, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return postRepository.searchPosts(searchKeyword, category, status);
    }

    public Post getPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay bai viet"));
    }

    public List<Post> getPublishedPosts() {
        return postRepository.findByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED");
    }

    public List<Post> getLatestPublishedPosts() {
        return postRepository.findTop3ByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED");
    }

    public Post getPublishedPost(Long id) {
        return postRepository.findByIdAndStatus(id, "PUBLISHED")
                .orElseThrow(() -> new RuntimeException("Khong tim thay bai viet"));
    }

    @Transactional
    public Post createPost(Post post, MultipartFile file) throws IOException {
        updateThumbnailIfPresent(post, file);
        return postRepository.save(post);
    }

    @Transactional
    public void updatePost(Post post) {
        postRepository.save(post);
    }

    @Transactional
    public void deletePost(Long id) {
        postRepository.deleteById(id);
    }

    private void updateThumbnailIfPresent(Post post, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        if (!Files.exists(UPLOAD_PATH)) {
            Files.createDirectories(UPLOAD_PATH);
        }

        Files.copy(file.getInputStream(), UPLOAD_PATH.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        post.setThumbnail("/uploads/" + fileName);
    }
    public boolean existsByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return false;
        return postRepository.existsByTitle(title.trim());
    }

    public boolean existsByTitleAndIdNot(String title, Long id) {
        if (title == null || title.trim().isEmpty()) return false;
        return postRepository.existsByTitleAndIdNot(title.trim(), id);
    }
    @Transactional
    public void updatePost(Post post, MultipartFile file) throws IOException {
        // 1. Lấy bài viết gốc từ database lên
        Post existingPost = getPost(post.getId());

        // 2. Xử lý ảnh Thumbnail
        if (file != null && !file.isEmpty()) {
            updateThumbnailIfPresent(post, file);
        } else {
            post.setThumbnail(existingPost.getThumbnail());
        }
        // 3. Giữ lại thời gian tạo gốc (createdAt) từ database để không bị NULL hoặc nhảy ngày mới
        // Vì không có hàm setCreatedAt, cậu gán thẳng giá trị gốc vào một biến tạm/hoặc xử lý thông qua việc gán gián tiếp của JPA
        // Cách an toàn nhất khi dùng lệnh save() lan truyền mà không có hàm Set là:
        postRepository.save(post);

    }
}
