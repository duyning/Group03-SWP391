/**
 * Service xử lý các tin tức, bài viết khuyến mãi và sự kiện rạp phim (`PostService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PostController` (Admin quản trị nội dung) và `PublicController` (Khách xem bài viết/tin tức trên trang chủ).
 * - Tương tác với `PostRepository` để tìm kiếm tin tức (`searchPosts`), lấy bài viết xuất bản mới nhất (`getLatestPublishedPosts`), kiểm tra trùng tiêu đề (`existsByTitle`).
 * - Lưu tập tin ảnh Thumbnail bài viết vào thư mục `uploads/`.
 */
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

    /** Tìm kiếm bài viết theo từ khóa, danh mục và trạng thái. */
    public List<Post> searchPosts(String keyword, String category, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return postRepository.searchPosts(searchKeyword, category, status);
    }

    /** Tìm chi tiết bài viết theo ID. */
    public Post getPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay bai viet"));
    }

    /** Lấy danh sách các bài viết đã xuất bản (`PUBLISHED`) xếp theo thời gian mới nhất. */
    public List<Post> getPublishedPosts() {
        return postRepository.findByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED");
    }

    /** Lấy top 3 bài viết mới nhất đã xuất bản để hiển thị trên Banner/Trang chủ. */
    public List<Post> getLatestPublishedPosts() {
        return postRepository.findTop3ByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED");
    }

    /** Lấy bài viết công khai theo ID (chỉ tìm bài có `status = PUBLISHED`). */
    public Post getPublishedPost(Long id) {
        return postRepository.findByIdAndStatus(id, "PUBLISHED")
                .orElseThrow(() -> new RuntimeException("Khong tim thay bai viet"));
    }

    /** Tạo mới một bài viết tin tức kèm ảnh đại diện thumbnail. */
    @Transactional
    public Post createPost(Post post, MultipartFile file) throws IOException {
        updateThumbnailIfPresent(post, file);
        return postRepository.save(post);
    }

    /** Lưu chỉnh sửa bài viết. */
    @Transactional
    public void updatePost(Post post) {
        postRepository.save(post);
    }

    /** Xóa một bài viết theo ID. */
    @Transactional
    public void deletePost(Long id) {
        postRepository.deleteById(id);
    }

    /** Xử lý tải ảnh thumbnail cho bài viết vào ổ đĩa. */
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

    /** Kiểm tra xem tiêu đề bài viết đã tồn tại chưa khi tạo mới. */
    public boolean existsByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return false;
        return postRepository.existsByTitle(title.trim());
    }

    /** Kiểm tra xem tiêu đề bài viết có trùng với bài viết khác hay không khi chỉnh sửa. */
    public boolean existsByTitleAndIdNot(String title, Long id) {
        if (title == null || title.trim().isEmpty()) return false;
        return postRepository.existsByTitleAndIdNot(title.trim(), id);
    }

    /** Cập nhật thông tin bài viết và ảnh đại diện mới (giữ lại thumbnail cũ nếu không tải ảnh mới). */
    @Transactional
    public void updatePost(Post post, MultipartFile file) throws IOException {
        Post existingPost = getPost(post.getId());

        if (file != null && !file.isEmpty()) {
            updateThumbnailIfPresent(post, file);
        } else {
            post.setThumbnail(existingPost.getThumbnail());
        }
        postRepository.save(post);
    }
}

