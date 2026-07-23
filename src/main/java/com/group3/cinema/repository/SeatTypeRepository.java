/**
 * Interface Repository thao tác dữ liệu cấu hình các loại ghế (`seat_types`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `RoomService` và `CatalogInitializer` để lấy màu sắc, sức chứa và tên loại ghế (std, vip, couple).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {

    /** Lấy tất cả loại ghế đang mở kinh doanh (`active = true`), sắp xếp theo ID. */
    List<SeatType> findByActiveTrueOrderByIdAsc();

    /** Lấy tất cả loại ghế trong CSDL, sắp xếp theo ID. */
    List<SeatType> findAllByOrderByIdAsc();

    /** Tìm loại ghế theo mã code (std, vip, couple). */
    Optional<SeatType> findByCodeIgnoreCase(String code);

    /** Kiểm tra xem mã loại ghế đã tồn tại hay chưa. */
    boolean existsByCodeIgnoreCase(String code);
}

