/**
 * Interface Repository thao tác cấu hình Ma trận Giá vé cơ sở (`ticket_price_configs`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PricingService` và `CustomerBookingService` để tính giá gốc của suất chiếu theo loại ngày và khung giờ chiếu.
 * 
 * Khởi tạo bởi: TrienLX
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.TicketPriceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TicketPriceConfigRepository extends JpaRepository<TicketPriceConfig, Long> {

    /** Tìm cấu hình giá vé theo loại ngày ("Trong tuần", "Cuối tuần", "Ngày lễ") và tên suất chiếu ("Suất sớm", "Giờ vàng"...). */
    Optional<TicketPriceConfig> findByDayTypeAndSlotName(String dayType, String slotName);
}

