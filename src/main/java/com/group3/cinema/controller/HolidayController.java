package com.group3.cinema.controller;

import com.group3.cinema.entity.Holiday;
import com.group3.cinema.service.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    // 1. Hiển thị trang quản lý ngày lễ
    @GetMapping
    public String showHolidayPage(Model model) {
        model.addAttribute("holidayList", holidayService.getAllHolidays());
        // Nếu model chưa có đối tượng newHoliday (do lỗi validate truyền sang), thì mới tạo mới
        if (!model.containsAttribute("newHoliday")) {
            model.addAttribute("newHoliday", new Holiday());
        }
        return "holiday-management";
    }

    // 2. Xử lý lưu ngày lễ mới (Có Validate)
    @PostMapping("/add")
    public String addHoliday(@Valid @ModelAttribute("newHoliday") Holiday holiday,
                             BindingResult result,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        // BƯỚC 1: Kiểm tra các ràng buộc Validate đầu vào (@NotBlank, @NotNull từ Entity)
        if (result.hasErrors()) {
            // Nạp lại danh sách để bảng bên phải không bị trống dữ liệu
            model.addAttribute("holidayList", holidayService.getAllHolidays());
            // Trả trực tiếp về view chứ KHÔNG redirect, nhằm giữ lại thông báo lỗi đỏ của HTML5/Thymeleaf nếu có mở rộng
            model.addAttribute("errorMessage", "Dữ liệu nhập vào không hợp lệ. Vui lòng kiểm tra lại!");
            return "holiday-management";
        }

        // BƯỚC 2: Kiểm tra logic nghiệp vụ dưới DB (Ví dụ: trùng ngày)
        try {
            holidayService.saveHoliday(holiday);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm ngày lễ mới thành công!");
        } catch (IllegalArgumentException e) {
            // Bắt lỗi trùng ngày từ Service ném lên
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            // Bắt các lỗi hệ thống không lường trước
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi lưu ngày lễ!");
        }

        return "redirect:/admin/holidays";
    }

    // 3. Xử lý xóa ngày lễ (Có bảo hiểm try-catch)
    @GetMapping("/delete/{id}")
    public String deleteHoliday(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            holidayService.deleteHoliday(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa ngày lễ thành công!");
        } catch (Exception e) {
            // Đề phòng trường hợp id không tồn tại hoặc bị ràng buộc dữ liệu khóa ngoại
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa ngày lễ này hoặc dữ liệu không tồn tại!");
        }
        return "redirect:/admin/holidays";
    }
}