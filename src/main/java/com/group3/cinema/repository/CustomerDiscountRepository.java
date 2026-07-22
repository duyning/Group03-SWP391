/**
 * Interface Repository thao tác dữ liệu cấu hình giảm giá theo loại khách hàng (`customer_discounts`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PricingService` và `CustomerBookingService` để tính tỷ lệ chiết khấu cho vé xem phim.
 * 
 * Khởi tạo bởi: TrienLX (25/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.CustomerDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerDiscountRepository extends JpaRepository<CustomerDiscount, Long> {

    /**
     * Tìm cấu hình giảm giá theo mã loại đối tượng khách hàng (STUDENT, CHILD, ELDERLY, ADULT).
     */
    Optional<CustomerDiscount> findByCustomerType(String customerType);
}

