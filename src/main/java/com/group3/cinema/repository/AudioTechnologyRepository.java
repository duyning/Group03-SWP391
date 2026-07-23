/**
 * Interface Repository thao tác dữ liệu công nghệ âm thanh phòng chiếu (`audio_technologies`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `RoomService` và `CatalogInitializer` để hiển thị/khởi tạo danh mục công nghệ âm thanh.
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.AudioTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudioTechnologyRepository extends JpaRepository<AudioTechnology, Long> {

    /**
     * Lấy danh sách công nghệ âm thanh đang hoạt động (`active = true`), sắp xếp tên A-Z.
     */
    List<AudioTechnology> findByActiveTrueOrderByNameAsc();

    /**
     * Lấy tất cả công nghệ âm thanh trong CSDL, sắp xếp theo tên A-Z.
     */
    List<AudioTechnology> findAllByOrderByNameAsc();

    /**
     * Tìm kiếm công nghệ âm thanh theo tên (không phân biệt chữ hoa chữ thường).
     */
    Optional<AudioTechnology> findByNameIgnoreCase(String name);

    /**
     * Kiểm tra sự tồn tại của công nghệ âm thanh theo tên (không phân biệt hoa thường).
     */
    boolean existsByNameIgnoreCase(String name);
}

