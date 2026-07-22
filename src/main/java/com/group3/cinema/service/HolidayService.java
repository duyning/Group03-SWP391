/**
 * Service quản lý danh sách Ngày lễ chiếu phim trong năm (`HolidayService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `HolidayController` (Quản trị Admin) và `TicketService`, `ShowtimeService` để xác định chính sách giá vé phụ thu ngày lễ.
 * - Tương tác với `HolidayRepository` để kiểm tra sự tồn tại của ngày lễ (`existsByHolidayDate`).
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Holiday;
import com.group3.cinema.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    @Autowired
    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /** Lấy danh sách tất cả các ngày lễ đã cấu hình. */
    @Transactional(readOnly = true)
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }

    /** Thêm mới một ngày lễ vào lịch hệ thống (chặn trùng lặp ngày). */
    @Transactional
    public void saveHoliday(Holiday holiday) {
        if (holidayRepository.existsByHolidayDate(holiday.getHolidayDate())) {
            throw new IllegalArgumentException("Ngày này đã được cấu hình là ngày lễ rồi!");
        }
        holidayRepository.save(holiday);
    }

    /** Xóa một ngày lễ khỏi danh sách theo ID. */
    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    /** Kiểm tra xem một ngày chỉ định có thuộc danh sách ngày lễ hay không. */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByHolidayDate(date);
    }
}