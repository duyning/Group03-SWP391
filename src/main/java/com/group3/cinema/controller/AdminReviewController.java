package com.group3.cinema.controller;

/*
 * Added on 2026-07-10: Admin movie review management screen and visibility controls.
 * This controller supports filtering, paging, and hiding/showing customer movie reviews.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MovieReview;
import com.group3.cinema.service.MovieReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {

    // Toàn bộ tìm kiếm, thống kê và ẩn/hiện review được gom trong service này.
    private final MovieReviewService movieReviewService;

    public AdminReviewController(MovieReviewService movieReviewService) {
        // Constructor injection giúp dependency bắt buộc và dễ thay bằng mock trong test.
        this.movieReviewService = movieReviewService;
    }

    @GetMapping
    /**
     * Hiển thị màn quản lý đánh giá với bộ lọc từ khóa, trạng thái và khoảng ngày.
     * Kết quả được phân trang trong bộ nhớ vì service đã kết hợp tìm kiếm không dấu
     * trên nhiều trường cùng các bí danh trạng thái VISIBLE/HIDDEN.
     */
    public String listReviews(@RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
                              @RequestParam(value = "startDate", required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                              @RequestParam(value = "endDate", required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                              @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                              @RequestParam(value = "size", required = false, defaultValue = "10") int size,
                              HttpSession session,
                              Model model) {
        // Lấy admin đang đăng nhập để header hiển thị thông tin tài khoản.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Service lọc keyword không dấu, trạng thái và khoảng ngày trước khi controller phân trang.
        List<MovieReview> filteredReviews = movieReviewService.searchReviewsForAdmin(keyword, status, startDate, endDate);

        // Chặn page size nhỏ nhất 5 và lớn nhất 50 để query string sửa tay không dựng quá nhiều card.
        int pageSize = Math.min(Math.max(size, 5), 50);

        // Tổng kết quả sau lọc, khác với tổng review toàn hệ thống.
        int totalReviews = filteredReviews.size();

        // Luôn giữ ít nhất một trang để UI phân trang không chia cho 0.
        int totalPages = Math.max(1, (int) Math.ceil((double) totalReviews / pageSize));

        // Chuẩn hóa page URL về khoảng hợp lệ.
        int currentPage = Math.min(Math.max(page, 1), totalPages);

        // Tính lát danh sách bắt đầu của trang hiện tại.
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalReviews);

        // Điểm kết thúc không được vượt kích thước list.
        int toIndex = Math.min(fromIndex + pageSize, totalReviews);

        // Hai query count riêng phục vụ các thẻ thống kê toàn hệ thống.
        long allReviewCount = movieReviewService.getTotalReviewCount();
        long visibleReviewCount = movieReviewService.getVisibleReviewCount();

        // User phục vụ header/sidebar.
        model.addAttribute("user", loggedInUser);

        // Chỉ truyền subList của trang hiện tại, không truyền toàn bộ kết quả.
        model.addAttribute("reviews", filteredReviews.subList(fromIndex, toIndex));

        // Các con số dưới đây lần lượt phục vụ nhãn kết quả và ba thẻ thống kê.
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("allReviewCount", allReviewCount);
        model.addAttribute("visibleReviewCount", visibleReviewCount);

        // Review không APPROVED được xem là đang ẩn.
        model.addAttribute("hiddenReviewCount", allReviewCount - visibleReviewCount);

        // Dữ liệu dựng link phân trang.
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);

        // Trả bộ lọc về template để input/select giữ trạng thái.
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        // Render templates/admin-review-list.html.
        return "admin-review-list";
    }

    @PostMapping("/{id}/visibility")
    /**
     * Ẩn hoặc khôi phục một đánh giá mà không xóa bản ghi.
     * Service lưu cả quản trị viên và thời điểm thao tác để phục vụ kiểm tra sau này.
     */
    public String updateVisibility(@PathVariable("id") Long id,
                                   @RequestParam("visible") boolean visible,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        // Admin identity lấy từ session, không lấy adminAccountId từ form.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Session hết hạn phải quay về login trước khi thay đổi dữ liệu.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        try {
            // Service tìm review/admin, đổi APPROVED hoặc REJECTED và ghi người/thời điểm thao tác.
            movieReviewService.setReviewVisible(id, loggedInUser.getAccountID(), visible);

            // Thông báo phụ thuộc vào trạng thái đích do form gửi.
            redirectAttributes.addFlashAttribute("successMessage",
                    visible ? "Đã hiển thị lại đánh giá." : "Đã ẩn đánh giá.");
        } catch (IllegalArgumentException exception) {
            // Review/admin không tồn tại được hiển thị dạng flash thay vì lỗi 500.
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        // PRG pattern: refresh trang sau POST không thực hiện thao tác lần hai.
        return "redirect:/admin/reviews";
    }
}
