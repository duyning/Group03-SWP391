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

    // Nếu cậu dùng Enum cho status ở file Product gốc thì đổi String thành Product.ProductStatus nhé!
    @Query("SELECT p FROM Product p WHERE " +
            "(:keyword IS NULL OR p.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR p.status = :status)")
    List<Product> searchProducts(@Param("keyword") String keyword, @Param("status") String status);
}