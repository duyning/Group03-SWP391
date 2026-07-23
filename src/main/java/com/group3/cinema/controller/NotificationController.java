package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller quản lý Trung tâm thông báo (Notification Center) dành cho Khách hàng/Người dùng.
 * Cung cấp các chức năng: Xem danh sách thông báo phân trang, đánh dấu đã đọc và điều hướng theo tác vụ.
 *
 * @author Group 3 - Cinema Management System
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // Sử dụng Constructor Injection để đảm bảo tính sẵn sàng của Service và dễ viết Unit Test
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Hiển thị danh sách thông báo của người dùng đang đăng nhập (có hỗ trợ phân trang).
     * Khi người dùng truy cập vào trang này, hệ thống sẽ tự động cập nhật tất cả thông báo thành 'Đã đọc'.
     *
     * @param page Trang hiện tại (mặc định là trang 0)
     * @param session HTTP Session dùng để lấy thông tin tài khoản đang đăng nhập
     * @param model Đối tượng truyền dữ liệu sang giao diện Thymeleaf
     * @return Tên view 'notification-list' hoặc chuyển hướng về '/login' nếu chưa đăng nhập
     */
    @GetMapping
    public String listNotifications(
            @RequestParam(value = "page", defaultValue = "0") int page,
            HttpSession session,
            Model model) {

        // 1. Kiểm tra xác thực: Nếu chưa đăng nhập thì chuyển hướng về trang Login
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        int accountId = sessionAccount.getAccountID();

        // 2. Nghiệp vụ: Tự động đánh dấu tất cả thông báo là 'Đã đọc' khi người dùng mở trung tâm thông báo
        notificationService.markAllAsRead(accountId);

        // 3. Lấy danh sách thông báo phân trang (mỗi trang 10 bản ghi) để hiển thị lên View
        model.addAttribute("notifications", notificationService.getUserNotifications(accountId, page, 10));

        // 4. Cập nhật lại số lượng thông báo chưa đọc về 0 trên giao diện
        model.addAttribute("unreadCount", 0);
        model.addAttribute("unreadNotificationCount", 0);

        return "notification-list";
    }

    /**
     * Xử lý khi người dùng nhấn vào một thông báo cụ thể.
     * Đánh dấu thông báo đó là 'Đã đọc' và điều hướng người dùng tới đường dẫn đính kèm (Action URL).
     *
     * @param id ID của thông báo cần xử lý
     * @param session HTTP Session để kiểm tra quyền sở hữu thông báo
     * @return Chuyển hướng tới Action URL (ví dụ: chi tiết vé, chi tiết đơn hàng) hoặc quay lại trang danh sách thông báo
     */
    @GetMapping("/read/{id}")
    public String readNotification(@PathVariable("id") Long id, HttpSession session) {
        // Kiểm tra trạng thái đăng nhập
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        // Lấy đường dẫn hành động (Deep-link) đính kèm trong thông báo (nếu có)
        String actionUrl = notificationService.getActionUrl(id, sessionAccount.getAccountID());

        // Cập nhật trạng thái thông báo này thành 'Đã đọc'
        notificationService.markAsRead(id);

        // Điều hướng thông minh: Nếu thông báo có link đính kèm thì mở link đó, ngược lại về trang danh sách
        if (actionUrl != null && !actionUrl.isBlank()) {
            return "redirect:" + actionUrl;
        }
        return "redirect:/notifications";
    }

    /**
     * Chức năng thao tác nhanh: Đánh dấu tất cả thông báo của người dùng là 'Đã đọc'.
     *
     * @param session HTTP Session để lấy thông tin tài khoản
     * @param redirectAttributes Dùng để truyền thông điệp thông báo (Flash Message) sang trang chuyển hướng
     * @return Chuyển hướng về trang danh sách thông báo `/notifications`
     */
    @GetMapping("/read-all")
    public String readAllNotifications(HttpSession session, RedirectAttributes redirectAttributes) {
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        // Thực thi cập nhật hàng loạt dưới CSDL
        notificationService.markAllAsRead(sessionAccount.getAccountID());

        // Gửi thông báo thành công qua Flash Attribute (chỉ tồn tại trong 1 lần redirect)
        redirectAttributes.addFlashAttribute("message", "Đã đánh dấu tất cả thông báo là đã đọc.");

        return "redirect:/notifications";
    }
}