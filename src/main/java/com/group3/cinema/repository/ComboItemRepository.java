package com.group3.cinema.repository;

/*
 * Created on 2026-06-21: Repository for combo item links.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository quản lý liên kết giữa Combo và Món ăn/Đồ uống lẻ (ComboItem).
 * Kế thừa JpaRepository để thao tác với bảng trung gian lưu các món thành phần trong Combo.
 *
 * @author NinhDD - HE186113
 */
public interface ComboItemRepository extends JpaRepository<ComboItem, Long> {

    /**
     * Kiểm tra xem một món lẻ (FoodItem) có đang thuộc về ít nhất một Combo nào hay không.
     *
     * [Nghiệp vụ Xóa món lẻ - Food Item Cascade]:
     * - Nếu trả về true: Món lẻ đã nằm trong Combo -> Không xóa cứng mà chuyển sang trạng thái INACTIVE (xóa mềm).
     * - Nếu trả về false: Món lẻ chưa từng nằm trong Combo nào -> Cho phép xóa cứng hoàn toàn khỏi CSDL.
     *
     * @param foodItemId ID của món ăn/đồ uống lẻ cần kiểm tra
     * @return true nếu món lẻ đang được dùng trong Combo, ngược lại trả về false
     */
    boolean existsByFoodItemId(Long foodItemId);
}