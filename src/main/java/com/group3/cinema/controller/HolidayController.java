package com.group3.cinema.controller;

import com.group3.cinema.entity.Holiday;
import com.group3.cinema.service.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Controller điều hướng và xử lý các yêu cầu HTTP liên quan đến Quản lý Ngày Lễ (Holiday Management).
 * Cung cấp giao diện quản trị Admin để hiển thị danh sách, thêm mới và xóa ngày lễ/ngày đặc biệt.
 *
 * @author Group 3 - Cinema Management System
 */
@Controller
@RequestMapping("/admin/holidays")
public class HolidayController {

    /** Service xử lý các quy tắc nghiệp vụ liên quan đến ngày lễ */
    private final HolidayService holidayService;

    /**
     * Constructor Injection tiêm phụ thuộc HolidayService.
     *
     * @param holidayService Service quản lý ngày lễ
     */
    @Autowired
    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    // 1. Hiển thị trang quản lý ngày lễ
    /**
     * Endpoint [GET] /admin/holidays: Hiển thị giao diện quản lý danh sách ngày lễ.
     * Nạp toàn bộ danh sách ngày lễ hiện có và chuẩn bị đối tượng binding form cho chức năng thêm mới.
     *
     * @param model Đối tượng Model chứa dữ liệu truyền sang giao diện Thymeleaf
     * @return Tên view template "holiday-management"
     */
    @GetMapping
    public String showHolidayPage(Model model) {
        model.addAttribute("holidayList", holidayService.getAllHolidays());
        // Nếu model chưa có đối tượng newHoliday (do lỗi validate truyền sang), thì mới
        // tạo mới
        if (!model.containsAttribute("newHoliday")) {
            model.addAttribute("newHoliday", new Holiday());
        }
        return "holiday-management";
    }

    // 2. Xử lý lưu ngày lễ mới (Có Validate)
    /**
     * Endpoint [POST] /admin/holidays/add: Tiếp nhận form thêm mới Ngày Lễ.
     * Thực hiện kiểm tra ràng buộc đầu vào (@Valid) và bắt lỗi nghiệp vụ (trùng lặp ngày).
     *
     * @param holiday Đối tượng ngày lễ nhận từ Form (@ModelAttribute)
     * @param result Chứa kết quả xác thực dữ liệu đầu vào (BindingResult)
     * @param model Đối tượng Model để trả lại view khi có lỗi
     * @param redirectAttributes Đối tượng lưu trữ thông báo phản hồi khi chuyển hướng (FlashAttributes)
     * @return Chuyển hướng lại trang danh sách hoặc trả về view hiển thị lỗi
     */
    @PostMapping("/add")
    public String addHoliday(@Valid @ModelAttribute("newHoliday") Holiday holiday,
                             BindingResult result,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        // BƯỚC 1: Kiểm tra các ràng buộc Validate đầu vào (@NotBlank, @NotNull từ
        // Entity)
        if (result.hasErrors()) {
            // Nạp lại danh sách để bảng bên phải không bị trống dữ liệu
            model.addAttribute("holidayList", holidayService.getAllHolidays());
            // Trả trực tiếp về view chứ KHÔNG redirect, nhằm giữ lại thông báo lỗi đỏ của
            // HTML5/Thymeleaf nếu có mở rộng
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
    /**
     * Endpoint [GET] /admin/holidays/delete/{id}: Thực hiện xóa ngày lễ khỏi hệ thống theo ID.
     * Bọc khối try-catch an toàn đề phòng các sự cố vi phạm khóa ngoại hoặc không tìm thấy ID.
     *
     * @param id ID của ngày lễ cần xóa (@PathVariable)
     * @param redirectAttributes Thông báo phản hồi sau khi thực hiện thao tác
     * @return Chuyển hướng về trang danh sách `/admin/holidays`
     */
    @GetMapping("/delete/{id}")
    public String deleteHoliday(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            holidayService.deleteHoliday(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa ngày lễ thành công!");
        } catch (Exception e) {
            // Đề phòng trường hợp id không tồn tại hoặc bị ràng buộc dữ liệu khóa ngoại
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Không thể xóa ngày lễ này hoặc dữ liệu không tồn tại!");
        }
        return "redirect:/admin/holidays";
    }
}