/**
 * Interface Repository thao tác bảng liên kết các món lẻ trong combo (`combo_items`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `FoodItemService` khi kiểm tra ràng buộc xem món đồ ăn lẻ (`FoodItem`) có đang nằm trong gói Combo nào không trước khi xóa.
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (21/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboItemRepository extends JpaRepository<ComboItem, Long> {

    /**
     * Kiểm tra xem một mặt hàng đồ ăn/thức uống lẻ (`foodItemId`) có đang thuộc bất kỳ combo nào hay không.
     * 
     * @param foodItemId ID sản phẩm đồ ăn lẻ.
     * @return true nếu mặt hàng đang nằm trong ít nhất 1 combo.
     */
    boolean existsByFoodItemId(Long foodItemId);
}

