package com.group3.cinema.repository;

/*
 * Created on 2026-06-21: Repository for combo food item catalog management.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Interface Repository quản lý truy vấn dữ liệu cho Entity FoodItem (Danh mục món ăn / đồ uống lẻ).
 * Kế thừa JpaRepository để thao tác CRUD và định nghĩa các Derived Query, JPQL tùy chỉnh.
 *
 * @author NinhDD - HE186113
 */
public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    /**
     * Kiểm tra xem tên món ăn/đồ uống đã tồn tại trong CSDL chưa (Không phân biệt chữ hoa/chữ thường).
     * Phục vụ kiểm tra ràng buộc duy nhất (Unique validation) khi THÊM MỚI món lẻ.
     *
     * @param name Tên món cần kiểm tra
     * @return true nếu tên đã tồn tại, ngược lại trả về false
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Kiểm tra xem tên món ăn/đồ uống đã tồn tại ở một bản ghi KHÁC hay chưa.
     * Phục vụ kiểm tra ràng buộc duy nhất khi CẬP NHẬT (Edit) thông tin món lẻ.
     *
     * @param name Tên món mới
     * @param id ID của món hiện tại đang chỉnh sửa
     * @return true nếu tên bị trùng với món khác, ngược lại trả về false
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Lấy danh sách các món lẻ theo tập hợp trạng thái truyền vào (VD: ACTIVE, NEW)
     * và sắp xếp tăng dần theo tên (A-Z).
     * Phục vụ nạp danh sách món lẻ khả dụng lên giao diện Tạo / Chỉnh sửa Combo.
     *
     * @param statuses Tập hợp trạng thái cần lọc (ví dụ: ["ACTIVE", "NEW"])
     * @return Danh sách món ăn/đồ uống phù hợp
     */
    List<FoodItem> findByStatusInOrderByNameAsc(Collection<String> statuses);

    /**
     * Truy vấn tìm kiếm và lọc danh sách món lẻ trong trang Quản lý Bắp nước (Admin Dashboard).
     * Hỗ trợ tìm kiếm linh hoạt theo Từ khóa (khớp với tên món hoặc danh mục) và Trạng thái kinh doanh.
     * Kết quả được sắp xếp tăng dần theo Danh mục, sau đó theo Tên món.
     *
     * @param keyword Từ khóa tìm kiếm (theo Tên món hoặc Danh mục - cho phép null/rỗng)
     * @param status Trạng thái kinh doanh (ACTIVE, NEW, INACTIVE - cho phép null/rỗng)
     * @return Danh sách các món ăn/đồ uống khớp với điều kiện lọc
     */
    @Query("SELECT f FROM FoodItem f WHERE " +
            "(:keyword IS NULL OR f.name LIKE %:keyword% OR f.category LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR f.status = :status) " +
            "ORDER BY f.category ASC, f.name ASC")
    List<FoodItem> searchFoodItems(@Param("keyword") String keyword,
                                   @Param("status") String status);
}