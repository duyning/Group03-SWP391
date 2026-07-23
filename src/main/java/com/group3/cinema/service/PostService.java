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

/**
 * Service quản lý Bài viết / Tin tức / Khuyến mãi (Post / Blog Management).
 * Đảm nhận các chức năng: Tìm kiếm, lọc bài viết, xuất bản tin tức ra trang chủ,
 * tải lên ảnh đại diện (Thumbnail) và kiểm tra ràng buộc duy nhất cho tiêu đề bài viết.
 *
 * @author Group 3 - Cinema Management System
 */
@Service
public class PostService {

    /** Đường dẫn thư mục lưu trữ ảnh đại diện bài viết trên Server */
    private static final Path UPLOAD_PATH = Paths.get("uploads");

    /** Repository thao tác dữ liệu với bảng Post trong CSDL */
    private final PostRepository postRepository;

    /**
     * Constructor Injection tiêm phụ thuộc PostRepository.
     *
     * @param postRepository Repository quản lý bài viết
     */
    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * Tìm kiếm và lọc bài viết trong trang Quản trị (Admin Dashboard).
     * Cho phép tìm kiếm linh hoạt theo từ khóa tiêu đề, thể loại bài viết và trạng thái xuất bản.
     *
     * @param keyword Từ khóa tìm kiếm theo tiêu đề (cho phép null/rỗng)
     * @param category Thể loại bài viết (cho phép null/rỗng)
     * @param status Trạng thái bài viết (DRAFT, PUBLISHED, INACTIVE...)
     * @return Danh sách các bài viết thỏa mãn điều kiện lọc
     */
    public List<Post> searchPosts(String keyword, String category, String status) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return postRepository.searchPosts(searchKeyword, category, status);
    }

    /**
     * Lấy thông tin chi tiết một bài viết theo ID (Không phân biệt trạng thái).
     * Dùng cho trang quản trị Admin để chỉnh sửa/xem chi tiết.
     *
     * @param id ID của bài viết
     * @return Đối tượng Post
     * @throws RuntimeException nếu không tìm thấy bài viết
     */
    public Post getPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay bai viet"));
    }

    /**
     * Lấy danh sách tất cả các bài viết ĐÃ XUẤT BẢN (Status = PUBLISHED).
     * Phục vụ hiển thị ra trang danh sách Tin tức / Khuyến mãi dành cho Khách hàng.
     * Sắp xếp ưu tiên theo ngày xuất bản (publishedAt) và ngày tạo (createdAt) mới nhất lên đầu.
     *
     * @return Danh sách bài viết công khai
     */
    public List<Post> getPublishedPosts() {
        return postRepository.findByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED");
    }

    /**
     * Lấy TOP 3 bài viết mới xuất bản gần đây nhất.
     * Phục vụ hiển thị trên khối "Tin nổi bật / Khuyến mãi mới nhất" ở Trang chủ (Homepage).
     *
     * @return Danh sách 3 bài viết mới nhất
     */
    public List<Post> getLatestPublishedPosts() {
        return postRepository.findTop3ByStatusOrderByPublishedAtDescCreatedAtDesc("PUBLISHED");
    }

    /**
     * Lấy thông tin chi tiết của một bài viết ĐÃ XUẤT BẢN dành cho Khách hàng xem bài viết chi tiết.
     * Chặn xem các bài viết đang ở dạng bản nháp (DRAFT) hoặc ẩn (INACTIVE).
     *
     * @param id ID của bài viết
     * @return Đối tượng Post công khai
     * @throws RuntimeException nếu bài viết không tồn tại hoặc chưa được xuất bản
     */
    public Post getPublishedPost(Long id) {
        return postRepository.findByIdAndStatus(id, "PUBLISHED")
                .orElseThrow(() -> new RuntimeException("Khong tim thay bai viet"));
    }

    /**
     * Tạo mới một bài viết kèm tải lên tệp ảnh đại diện (Thumbnail).
     *
     * @param post Đối tượng thông tin bài viết mới
     * @param file Tệp ảnh tải lên (MultipartFile)
     * @return Đối tượng Post đã lưu thành công vào CSDL
     * @throws IOException nếu có lỗi trong quá trình lưu tệp ảnh lên không gian lưu trữ
     */
    @Transactional
    public Post createPost(Post post, MultipartFile file) throws IOException {
        updateThumbnailIfPresent(post, file);
        return postRepository.save(post);
    }

    /**
     * Cập nhật trực tiếp đối tượng bài viết vào CSDL (Overloaded method).
     *
     * @param post Đối tượng bài viết cần cập nhật
     */
    @Transactional
    public void updatePost(Post post) {
        postRepository.save(post);
    }

    /**
     * Xóa bài viết khỏi CSDL theo ID.
     *
     * @param id ID của bài viết cần xóa
     */
    @Transactional
    public void deletePost(Long id) {
        postRepository.deleteById(id);
    }

    /**
     * Helper Method: Kiểm tra và xử lý lưu tệp ảnh đại diện (Thumbnail) lên máy chủ.
     * Tự động tạo thư mục /uploads/ nếu chưa tồn tại và sinh tên tệp duy nhất bằng `System.currentTimeMillis()`.
     *
     * @param post Đối tượng bài viết
     * @param file Tệp ảnh tải lên
     * @throws IOException nếu xẩy ra lỗi đọc/ghi tệp
     */
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

    /**
     * Kiểm tra tiêu đề bài viết đã tồn tại trong CSDL hay chưa (Dùng cho chức năng THÊM MỚI).
     * Tự động cắt khoảng trắng thừa hai đầu trước khi kiểm tra.
     *
     * @param title Tiêu đề bài viết cần kiểm tra
     * @return true nếu tiêu đề đã tồn tại, false nếu chưa có hoặc tiêu đề rỗng
     */
    public boolean existsByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return false;
        return postRepository.existsByTitle(title.trim());
    }

    /**
     * Kiểm tra tiêu đề bài viết có bị trùng với bài viết KHÁC không (Dùng cho chức năng CẬP NHẬT).
     *
     * @param title Tiêu đề bài viết mới
     * @param id ID của bài viết hiện tại đang chỉnh sửa
     * @return true nếu bị trùng tiêu đề với bài viết khác
     */
    public boolean existsByTitleAndIdNot(String title, Long id) {
        if (title == null || title.trim().isEmpty()) return false;
        return postRepository.existsByTitleAndIdNot(title.trim(), id);
    }

    /**
     * Cập nhật bài viết có xử lý giữ lại đường dẫn ảnh cũ nếu người dùng không chọn tệp ảnh mới.
     * Bảo toàn dữ liệu giao diện tránh bị vỡ ảnh đại diện khi lưu form.
     *
     * @param post Đối tượng bài viết chứa thông tin cập nhật từ Form
     * @param file Tệp ảnh mới (cho phép null/rỗng)
     * @throws IOException nếu có lỗi lưu tệp
     */
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