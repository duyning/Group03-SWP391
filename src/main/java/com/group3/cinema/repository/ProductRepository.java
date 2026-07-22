/**
 * Interface Repository quản lý các món sản phẩm đồ ăn / bắp nước bán lẻ (`products`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ProductService` và `ComboService` khi lấy danh sách sản phẩm hiển thị trên Menu bán lẻ hoặc tạo gói Combo.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Lấy danh sách món ăn/nước uống theo trạng thái kinh doanh (ví dụ: "ACTIVE" - Đang bán).
     * Dùng để đổ dữ liệu Menu chọn sản phẩm khi cấu hình gói Combo.
     */
    List<Product> findByStatus(String status);

    /**
     * Tìm kiếm danh sách món đồ ăn bán lẻ theo tên và trạng thái kinh doanh dành cho trang quản lý Admin/Manager.
     * 
     * @param keyword Từ khóa tên món.
     * @param status Trạng thái kinh doanh (ACTIVE, INACTIVE hoặc null).
     * @return Danh sách Product khớp với điều kiện.
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:keyword IS NULL OR p.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR p.status = :status)")
    List<Product> searchProducts(@Param("keyword") String keyword, @Param("status") String status);
}