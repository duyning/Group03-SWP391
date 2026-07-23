package com.group3.cinema.service;

import com.group3.cinema.entity.Holiday;
import com.group3.cinema.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service quản lý danh sách các Ngày Lễ / Ngày Đặc Biệt (Holiday Management).
 * Phục vụ nghiệp vụ tính giá vé, áp dụng chính sách giá/phụ thu riêng cho ngày lễ trên hệ thống rạp.
 *
 * @author Group 3 - Cinema Management System
 */
@Service
public class HolidayService { // Đổi thành Class thường giống các service khác của cậu

    /** Repository thao tác dữ liệu với bảng Holiday trong CSDL */
    private final HolidayRepository holidayRepository;

    /**
     * Constructor Injection để tiêm phụ thuộc HolidayRepository.
     *
     * @param holidayRepository Repository quản lý ngày lễ
     */
    @Autowired
    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /**
     * Lấy toàn bộ danh sách các ngày lễ đã được cấu hình trong hệ thống.
     * Sử dụng readOnly = true để tối ưu hóa hiệu năng truy vấn CSDL.
     *
     * @return Danh sách các đối tượng Holiday
     */
    @Transactional(readOnly = true)
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }

    /**
     * Thêm mới một ngày lễ vào CSDL.
     * Thực hiện kiểm tra ràng buộc duy nhất: Nếu ngày này đã được cấu hình trước đó,
     * hệ thống sẽ quăng ngoại lệ để chặn tạo trùng lặp.
     *
     * @param holiday Đối tượng ngày lễ cần lưu
     * @throws IllegalArgumentException nếu ngày lễ đã tồn tại trong hệ thống
     */
    @Transactional
    public void saveHoliday(Holiday holiday) {
        // Kiểm tra xem ngày này đã tồn tại trong DB chưa trước khi lưu
        if (holidayRepository.existsByHolidayDate(holiday.getHolidayDate())) {
            throw new IllegalArgumentException("Ngày này đã được cấu hình là ngày lễ rồi!");
        }
        holidayRepository.save(holiday);
    }

    /**
     * Xóa một ngày lễ khỏi CSDL theo ID.
     *
     * @param id ID của ngày lễ cần xóa
     */
    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    /**
     * Kiểm tra một ngày cụ thể (LocalDate) có phải là Ngày Lễ hay không.
     * Phục vụ Core Engine tính giá vé (Ví dụ: Nếu là ngày lễ thì áp dụng Phụ thu / Giá vé Ngày lễ).
     *
     * @param date Ngày cần kiểm tra
     * @return true nếu là ngày lễ, false nếu là ngày thường
     */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByHolidayDate(date);
    }
}