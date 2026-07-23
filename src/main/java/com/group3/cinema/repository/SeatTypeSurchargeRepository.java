/**
 * Interface Repository quản lý phụ thu theo loại ghế (`seat_type_surcharges`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PricingService` và `CustomerBookingService` để truy vấn số tiền cộng thêm cho ghế VIP / Couple.
 * 
 * Khởi tạo bởi: TrienLX (25/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.SeatTypeSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatTypeSurchargeRepository extends JpaRepository<SeatTypeSurcharge, Long> {

    /** Tìm cấu hình phụ thu theo mã loại ghế ("std", "vip", "couple"). */
    Optional<SeatTypeSurcharge> findBySeatTypeCode(String seatTypeCode);
}

