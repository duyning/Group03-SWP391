/**
 * Interface Repository quản lý thông tin Bài viết / Tin tức rạp chiếu phim (`posts`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PostService`, `PublicContentInitializer` để hiển thị tin tức ở trang chủ/trang tin tức và quản lý bài đăng Admin.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Tìm kiếm bài viết đa điều kiện theo từ khóa tiêu đề, danh mục và trạng thái phục vụ trang quản lý Admin/Manager.
     */
    @Query("SELECT p FROM Post p WHERE " +
            "(:keyword IS NULL OR p.title LIKE %:keyword%) AND " +
            "(:category IS NULL OR :category = '' OR p.category = :category) AND " +
            "(:status IS NULL OR :status = '' OR p.status = :status)")
    List<Post> searchPosts(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("status") String status);

    /**
     * Lấy danh sách các bài viết đã xuất bản (`status = PUBLISHED`), sắp xếp theo thời gian xuất bản mới nhất.
     */
    List<Post> findByStatusOrderByPublishedAtDescCreatedAtDesc(String status);

    /**
     * Lấy 3 bài viết mới nhất đã xuất bản để hiển thị lên mục Tin Tức Điện Ảnh ở Trang chủ.
     */
    List<Post> findTop3ByStatusOrderByPublishedAtDescCreatedAtDesc(String status);

    /**
     * Tìm một bài viết công khai theo ID và trạng thái (chặn khách xem bài nháp DRAFT).
     */
    Optional<Post> findByIdAndStatus(Long id, String status);

    /**
     * Kiểm tra trùng tiêu đề bài viết khi tạo mới.
     */
    boolean existsByTitle(String title);

    /**
     * Kiểm tra trùng tiêu đề bài viết với bài khác khi cập nhật.
     */
    boolean existsByTitleAndIdNot(String title, Long id);
}

