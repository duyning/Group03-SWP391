package com.group3.cinema.repository;

import com.group3.cinema.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    // Hàm kiểm tra nhanh xem ngày này đã được Admin cấu hình là ngày lễ chưa
    boolean existsByHolidayDate(LocalDate holidayDate);
}