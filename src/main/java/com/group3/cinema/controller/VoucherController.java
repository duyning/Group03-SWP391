package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.entity.Voucher.DiscountType;
import com.group3.cinema.entity.Voucher.ServiceScope;
import com.group3.cinema.entity.Voucher.ApplicableDay;
import com.group3.cinema.service.VoucherService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
@RequestMapping("/admin/vouchers")
public class VoucherController {

    private final VoucherService voucherService;

    @Autowired
    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    /**
     * HÀM BỔ TRỢ: Đổ động danh sách các Inner Enum từ Entity Voucher vào Model
     */
    private void populateEnums(Model model) {
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("serviceScopes", ServiceScope.values());
        model.addAttribute("applicableDaysOptions", ApplicableDay.values());
    }

    /**
     * 1. TRANG DANH SÁCH VOUCHER (ĐÃ CẬP NHẬT BỘ LỌC TÌM KIẾM)
     * Đường dẫn file HTML: src/main/resources/templates/voucher-list.html
     */
    @GetMapping({ "", "/list" })
    public String listVouchers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "discountType", required = false) String discountType,
            @RequestParam(value = "serviceScope", required = false) String serviceScope,
            Model model) {

        // 1. Gọi Service thực hiện tìm kiếm lọc động theo tham số truyền lên URL
        List<Voucher> vouchers = voucherService.searchVouchers(keyword, discountType, serviceScope);
        model.addAttribute("voucherList", vouchers);

        // 2. Nạp các Enum để hiển thị dữ liệu cho các ô Select của thanh Filter Bar bên
        // HTML
        populateEnums(model);

        return "voucher-list";
    }

    /**
     * 2. GIAO DIỆN TẠO VOUCHER MỚI
     * Đường dẫn file HTML: src/main/resources/templates/voucher-form.html
     */
    @GetMapping("/add")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("voucher")) {
            model.addAttribute("voucher", new Voucher());
        }
        populateEnums(model); // Đổ danh sách enum xuống form tạo mới
        return "voucher-form";
    }

    /**
     * 3. XỬ LÝ LƯU VOUCHER MỚI
     */
    @PostMapping("/add")
    public String createVoucher(@Valid @ModelAttribute("voucher") Voucher voucher,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateEnums(model); // Nạp lại danh sách enum để tránh trắng dữ liệu Dropdown select khi lỗi
                                  // validate
            return "voucher-form";
        }

        try {
            voucherService.saveVoucher(voucher);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo chương trình ưu đãi Voucher thành công!");
            return "redirect:/admin/vouchers/list";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("voucher", voucher);
            return "redirect:/admin/vouchers/add";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Đã xảy ra lỗi bất ngờ hệ thống không thể lưu voucher!");
            return "redirect:/admin/vouchers/add";
        }
    }

    /**
     * 4. GIAO DIỆN CHỈNH SỬA VOUCHER
     * Dùng chung file HTML: src/main/resources/templates/voucher-form.html
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            if (!model.containsAttribute("voucher")) {
                Voucher voucher = voucherService.getVoucherById(id);
                model.addAttribute("voucher", voucher);
            }
            populateEnums(model); // Đổ danh sách enum xuống form chỉnh sửa
            return "voucher-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/vouchers/list";
        }
    }

    /**
     * 5. XỬ LÝ CẬP NHẬT VOUCHER
     */
    @PostMapping("/edit/{id}")
    public String updateVoucher(@PathVariable("id") Long id,
            @Valid @ModelAttribute("voucher") Voucher voucher,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateEnums(model); // Nạp lại danh sách enum nếu lỗi binding dữ liệu sửa
            return "voucher-form";
        }

        try {
            voucherService.updateVoucher(id, voucher);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin Voucher thành công!");
            return "redirect:/admin/vouchers/list";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("voucher", voucher);
            return "redirect:/admin/vouchers/edit/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi hệ thống! Không thể cập nhật voucher.");
            return "redirect:/admin/vouchers/edit/" + id;
        }
    }

    /**
     * 6. XỬ LÝ XÓA VOUCHER
     */
    @GetMapping("/delete/{id}")
    public String deleteVoucher(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            // Gọi service xử lý ẩn mã
            voucherService.deleteVoucher(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã ẩn mã Voucher thành công ra khỏi hệ thống công khai!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi ẩn voucher!");
        }
        return "redirect:/admin/vouchers/list";
    }

}