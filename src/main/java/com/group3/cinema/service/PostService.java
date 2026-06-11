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
    public void createPost(Post post, MultipartFile file) throws IOException {
        updateThumbnailIfPresent(post, file);
        postRepository.save(post);
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
}
