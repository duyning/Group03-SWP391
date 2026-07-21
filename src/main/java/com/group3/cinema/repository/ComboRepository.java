package com.group3.cinema.repository;

import com.group3.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Interface Repository quản lý các truy vấn dữ liệu cho Entity Combo (Gói bắp nước/ưu đãi).
 * Kế thừa JpaRepository để sử dụng các hàm CRUD cơ bản và định nghĩa các truy vấn JPQL tùy chỉnh.
 *
 * @author Group 3 - Cinema Management System
 */
public interface ComboRepository extends JpaRepository<Combo, Long> {

    /**
     * Lấy danh sách Combo theo tập hợp các trạng thái truyền vào (VD: ACTIVE, NEW)
     * và sắp xếp tăng dần theo tên Combo.
     *
     * @param statuses Danh sách trạng thái cần lọc
     * @return Danh sách Combo thỏa mãn điều kiện
     */
    List<Combo> findByStatusInOrderByNameAsc(List<String> statuses);

    // 1. Kiểm tra xem tên combo đã tồn tại chưa (phục vụ lúc tạo mới)
    boolean existsByName(String name);

    // 2. Kiểm tra xem tên combo đã tồn tại ở một bản ghi khác chưa (phục vụ lúc edit)
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Truy vấn chi tiết Combo kèm theo danh sách món (ComboItem) và thông tin món ăn lẻ (FoodItem).
     * Sử dụng LEFT JOIN FETCH để nạp dữ liệu liên quan trong 1 câu lệnh duy nhất (tránh lỗi N+1 Query & LazyInitializationException).
     *
     * @param id ID của Combo cần tìm
     * @return Optional chứa đối tượng Combo đầy đủ thông tin các món đính kèm
     */
    @Query("SELECT DISTINCT c FROM Combo c " +
            "LEFT JOIN FETCH c.items i " +
            "LEFT JOIN FETCH i.foodItem " +
            "WHERE c.id = :id")
    Optional<Combo> findWithItemsById(@Param("id") Long id);

    /**
     * Truy vấn tìm kiếm và lọc Combo linh hoạt trong trang quản trị Admin.
     * Hỗ trợ lọc theo từ khóa tên Combo và Trạng thái kinh doanh.
     *
     * @param keyword Từ khóa tìm kiếm theo tên Combo (cho phép null/rỗng)
     * @param status Trạng thái Combo (ACTIVE, NEW, INACTIVE - cho phép null/rỗng)
     * @return Danh sách các Combo khớp với điều kiện lọc
     */
    @Query("SELECT c FROM Combo c WHERE " +
            "(:keyword IS NULL OR c.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR c.status = :status)")
    List<Combo> searchCombos(@Param("keyword") String keyword,
                             @Param("status") String status);
}