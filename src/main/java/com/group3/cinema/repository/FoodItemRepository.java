/**
 * Interface Repository thao tác dữ liệu danh mục đồ ăn / nước uống đơn lẻ (`food_items`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `FoodItemService` và `ComboService` để lấy danh sách sản phẩm lẻ ghép combo.
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (21/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    /**
     * Kiểm tra xem tên sản phẩm đồ ăn/uống đã tồn tại trong hệ thống chưa khi tạo mới.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Kiểm tra tên sản phẩm có bị trùng lặp với sản phẩm khác (ngoại trừ ID `id`) khi cập nhật.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Lấy các sản phẩm đồ ăn thuộc tập hợp trạng thái chỉ định, sắp xếp tên A-Z.
     */
    List<FoodItem> findByStatusInOrderByNameAsc(Collection<String> statuses);

    /**
     * Tìm kiếm sản phẩm đồ ăn/uống theo từ khóa (tên hoặc danh mục) và trạng thái kinh doanh dành cho trang quản trị Admin/Manager.
     * 
     * @param keyword Từ khóa tìm kiếm.
     * @param status Trạng thái (ACTIVE / INACTIVE / null).
     * @return Danh sách sản phẩm thỏa mãn điều kiện, sắp xếp theo danh mục và tên.
     */
    @Query("SELECT f FROM FoodItem f WHERE " +
            "(:keyword IS NULL OR f.name LIKE %:keyword% OR f.category LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR f.status = :status) " +
            "ORDER BY f.category ASC, f.name ASC")
    List<FoodItem> searchFoodItems(@Param("keyword") String keyword,
                                   @Param("status") String status);
}

