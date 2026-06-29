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
     * Lấy danh sách món ăn/nước uống theo trạng thái kinh doanh
     * Dùng để đổ dữ liệu Menu (ACTIVE) lên form chọn của Combo
     */
    List<Product> findByStatus(String status);

    /**
     * Tìm kiếm món lẻ theo từ khóa (tên món) và trạng thái.
     * Thỏa mãn bộ lọc tìm kiếm tại trang danh sách (product-list.html).
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:status IS NULL OR p.status = :status) " +
            "ORDER BY p.createdAt DESC")
    List<Product> searchProducts(@Param("keyword") String keyword, @Param("status") String status);
}