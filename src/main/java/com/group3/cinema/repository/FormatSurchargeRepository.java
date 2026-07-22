/**
 * Interface Repository thao tác bảng cấu hình phụ thu định dạng phim (`format_surcharges`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PricingService` và `CustomerBookingService` để tính số tiền phụ thu định dạng (2D, 3D, IMAX, Gold).
 * 
 * Khởi tạo bởi: TrienLX
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.FormatSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FormatSurchargeRepository extends JpaRepository<FormatSurcharge, Long> {

    /**
     * Tìm cấu hình phụ thu theo mã định dạng (ví dụ: "2D", "3D", "IMAX").
     */
    Optional<FormatSurcharge> findByFormatCode(String formatCode);
}

