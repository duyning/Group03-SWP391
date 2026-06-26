package com.group3.cinema.service;

import com.group3.cinema.entity.Holiday;
import com.group3.cinema.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class HolidayService { // Đổi thành Class thường giống các service khác của cậu

    private final HolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }

    @Transactional
    public void saveHoliday(Holiday holiday) {
        // Kiểm tra xem ngày này đã tồn tại trong DB chưa trước khi lưu
        if (holidayRepository.existsByHolidayDate(holiday.getHolidayDate())) {
            throw new IllegalArgumentException("Ngày này đã được cấu hình là ngày lễ rồi!");
        }
        holidayRepository.save(holiday);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByHolidayDate(date);
    }
}