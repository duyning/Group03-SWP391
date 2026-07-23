/**
 * Interface Repository quản lý thông tin danh sách Ngày Lễ Tết trong hệ thống (`holidays`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `HolidayService`, `PricingService`, `VoucherService` để kiểm tra điều kiện áp dụng giá ngày lễ và áp dụng mã giảm giá.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /**
     * Kiểm tra xem một ngày dương lịch chỉ định có phải là Ngày Lễ Tết trong CSDL hay không.
     * 
     * @param holidayDate Ngày cần kiểm tra (LocalDate).
     * @return true nếu ngày đó là ngày lễ.
     */
    boolean existsByHolidayDate(LocalDate holidayDate);
}