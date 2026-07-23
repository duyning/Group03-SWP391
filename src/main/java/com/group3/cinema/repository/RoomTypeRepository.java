/**
 * Interface Repository thao tác bảng danh mục định dạng / loại phòng chiếu (`room_types`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `RoomService` và `CatalogInitializer` để lấy các loại phòng chiếu (2D, 3D, IMAX).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    /** Lấy tất cả loại phòng chiếu đang hoạt động (`active = true`), sắp xếp tên A-Z. */
    List<RoomType> findByActiveTrueOrderByNameAsc();

    /** Lấy tất cả loại phòng chiếu trong CSDL, sắp xếp tên A-Z. */
    List<RoomType> findAllByOrderByNameAsc();

    /** Tìm loại phòng chiếu theo tên (không phân biệt chữ hoa/thường). */
    Optional<RoomType> findByNameIgnoreCase(String name);

    /** Kiểm tra xem tên loại phòng chiếu đã tồn tại hay chưa. */
    boolean existsByNameIgnoreCase(String name);
}

