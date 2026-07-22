/**
 * Interface Repository quản lý thông tin các gói Combo bắp nước (`combos`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ComboService`, `CustomerBookingService`, `PublicContentInitializer`.
 * - Hỗ trợ nạp đầy đủ cấu trúc danh sách món đồ ăn trong combo (`findWithItemsById`),
 *   tìm kiếm và lọc danh sách combo theo từ khóa và trạng thái (`searchCombos`).
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    /**
     * Lấy danh sách Combo theo tập hợp các trạng thái chỉ định, sắp xếp tên A-Z (thường dùng lấy danh sách ACTIVE cho khách xem).
     */
    List<Combo> findByStatusInOrderByNameAsc(List<String> statuses);

    /**
     * Kiểm tra xem tên combo đã tồn tại trong CSDL chưa khi tạo mới.
     */
    boolean existsByName(String name);

    /**
     * Kiểm tra tên combo có bị trùng với gói combo khác (ngoại trừ ID hiện tại `id`) khi cập nhật.
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Nạp đối tượng Combo kèm theo tập hợp `items` và thông tin `foodItem` chi tiết bằng JOIN FETCH.
     * Tránh LazyInitializationException khi hiển thị giao diện xem chi tiết gói combo.
     * 
     * @param id ID gói combo.
     * @return Optional chứa Combo đầy đủ thành phần.
     */
    @Query("SELECT DISTINCT c FROM Combo c " +
            "LEFT JOIN FETCH c.items i " +
            "LEFT JOIN FETCH i.foodItem " +
            "WHERE c.id = :id")
    Optional<Combo> findWithItemsById(@Param("id") Long id);

    /**
     * Tìm kiếm danh sách Combo theo từ khóa tên và trạng thái hoạt động dành cho trang quản lý Admin/Manager.
     * 
     * @param keyword Từ khóa tìm kiếm tên combo.
     * @param status Trạng thái (ACTIVE / INACTIVE / null).
     * @return Danh sách Combo thỏa mãn điều kiện.
     */
    @Query("SELECT c FROM Combo c WHERE " +
            "(:keyword IS NULL OR c.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR c.status = :status)")
    List<Combo> searchCombos(@Param("keyword") String keyword,
                             @Param("status") String status);
}

