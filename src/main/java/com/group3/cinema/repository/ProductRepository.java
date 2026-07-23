package com.group3.cinema.repository;

import com.group3.cinema.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Interface Repository quản lý các thao tác truy vấn dữ liệu cho Entity Product (Sản phẩm / Món lẻ).
 * Kế thừa JpaRepository để cung cấp các hàm CRUD cơ bản và các câu lệnh truy vấn JPQL tùy chỉnh.
 *
 * @author Group 3 - Cinema Management System
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Lấy danh sách món ăn/nước uống theo trạng thái kinh doanh chỉ định.
     * Dùng để đổ dữ liệu Menu sản phẩm (đang ở trạng thái ACTIVE) lên form chọn của Combo hoặc danh sách bắp nước.
     *
     * @param status Trạng thái kinh doanh của sản phẩm (ví dụ: ACTIVE, INACTIVE)
     * @return Danh sách các sản phẩm thỏa mãn trạng thái
     */
    List<Product> findByStatus(String status);

    /**
     * Truy vấn tìm kiếm sản phẩm động trong trang Quản trị (Admin Dashboard).
     * Hỗ trợ lọc kết hợp theo từ khóa tên sản phẩm và trạng thái kinh doanh.
     * Xử lý an toàn khi các tham số truyền vào bị NULL (khi người dùng không nhập lọc).
     *
     * @param keyword Từ khóa tìm kiếm theo tên sản phẩm (cho phép null)
     * @param status Trạng thái kinh doanh cần lọc (cho phép null)
     * @return Danh sách các sản phẩm phù hợp với điều kiện tìm kiếm
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:keyword IS NULL OR p.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR p.status = :status)")
    List<Product> searchProducts(@Param("keyword") String keyword, @Param("status") String status);
}