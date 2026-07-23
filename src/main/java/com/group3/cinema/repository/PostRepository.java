package com.group3.cinema.repository;

import com.group3.cinema.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Interface Repository quản lý truy vấn dữ liệu cho Entity Post (Bài viết / Tin tức).
 * Kế thừa JpaRepository để sử dụng các hàm CRUD cơ bản và hỗ trợ viết JPQL / Derived Queries.
 *
 * @author Group 3 - Cinema Management System
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Truy vấn tìm kiếm bài viết động trong trang Quản trị (Admin Dashboard).
     * Cho phép lọc kết hợp 3 điều kiện: Từ khóa tiêu đề, Danh mục và Trạng thái.
     *
     * @param keyword Từ khóa tìm kiếm theo tiêu đề bài viết (hoặc null/rỗng)
     * @param category Danh mục tin tức cần lọc (hoặc null/rỗng)
     * @param status Trạng thái bài viết: DRAFT, PUBLISHED, HIDDEN (hoặc null/rỗng)
     * @return Danh sách bài viết thỏa mãn các điều kiện tìm kiếm
     */
    @Query("SELECT p FROM Post p WHERE " +
            "(:keyword IS NULL OR p.title LIKE %:keyword%) AND " +
            "(:category IS NULL OR :category = '' OR p.category = :category) AND " +
            "(:status IS NULL OR :status = '' OR p.status = :status)")
    List<Post> searchPosts(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("status") String status);

    /**
     * Lấy tất cả bài viết theo trạng thái chỉ định, sắp xếp giảm dần theo thời gian xuất bản và thời gian tạo.
     * Thường dùng để hiển thị danh sách tin tức công khai cho Khách hàng.
     */
    List<Post> findByStatusOrderByPublishedAtDescCreatedAtDesc(String status);

    /**
     * Lấy Top 3 bài viết mới nhất theo trạng thái.
     * Thường dùng để hiển thị khối "Tin tức nổi bật" trên Trang chủ (Homepage Widget).
     */
    List<Post> findTop3ByStatusOrderByPublishedAtDescCreatedAtDesc(String status);

    /**
     * Tìm bài viết theo ID và Trạng thái cụ thể.
     * Giúp đảm bảo khách hàng chỉ xem được chi tiết các bài viết ở trạng thái đã xuất bản (PUBLISHED).
     */
    Optional<Post> findByIdAndStatus(Long id, String status);

    /**
     * Kiểm tra xem tiêu đề bài viết đã tồn tại trong CSDL chưa.
     * Phục vụ validate tránh trùng lặp tiêu đề khi Tạo bài viết mới.
     */
    boolean existsByTitle(String title);

    /**
     * Kiểm tra xem tiêu đề bài viết đã tồn tại ở một bản ghi bài viết KHÁC chưa.
     * Phục vụ validate tránh trùng lặp tiêu đề khi Chỉnh sửa (Edit) bài viết.
     */
    boolean existsByTitleAndIdNot(String title, Long id);
}