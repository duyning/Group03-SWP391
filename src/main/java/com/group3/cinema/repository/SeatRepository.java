/**
 * Interface Repository thao tác bảng danh sách vị trí ghế trong phòng chiếu (`seats`).
 * 
 * Luồng gọi & Sử dụng:
 * - `BookingShowtimeService` đọc sơ đồ để tính tổng sức chứa còn bán.
 * - `SeatHoldingService` đọc/kiểm tra ID ghế, tọa độ, loại ghế và dựng `BookingSeatView`.
 * - `RoomService` và màn quản trị dùng để lưu/reset sơ đồ phòng.
 * - `CatalogInitializer` dùng khi khởi tạo dữ liệu cấu hình ban đầu.
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * Lấy tất cả danh sách ghế của một phòng chiếu, sắp xếp tăng dần theo chỉ số hàng và cột (`rowIndex`, `colIndex`).
     */
    List<Seat> findByRoomIdOrderByRowIndexAscColIndexAsc(Long roomId);

    /**
     * Xóa toàn bộ sơ đồ ghế của phòng chiếu khi Admin lưu sơ đồ cấu hình ghế mới.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Seat s WHERE s.roomId = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);

    /**
     * Đếm số lượng ghế thuộc một loại cụ thể (std, vip, couple) trong phòng.
     */
    long countByRoomIdAndSeatType(Long roomId, String seatType);

    /**
     * Kiểm tra xem phòng chiếu đã được tạo sơ đồ ghế hay chưa.
     */
    boolean existsByRoomId(Long roomId);
}

