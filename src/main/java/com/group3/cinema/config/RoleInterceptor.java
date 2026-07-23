/**
 * Bộ chặn (Interceptor) dùng để kiểm tra vai trò (Role) người dùng trước khi truy cập tài nguyên.
 * Đảm bảo chỉ những người dùng đã đăng nhập và có quyền ADMIN hoặc MANAGER mới được truy cập các trang quản trị.
 * 
 * Lớp này triển khai HandlerInterceptor của Spring MVC.
 */
package com.group3.cinema.config;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class RoleInterceptor implements HandlerInterceptor {

    /**
     * Phương thức tiền xử lý request (chạy trước khi Controller nhận request).
     * 
     * Quy trình xử lý:
     * 1. Lấy thông tin session hiện tại từ request (`request.getSession()`).
     * 2. Kiểm tra thuộc tính `loggedInUser` trong session.
     * 3. Nếu người dùng chưa đăng nhập -> gọi `response.sendRedirect()` chuyển hướng về `/login`.
     * 4. Nếu đã đăng nhập nhưng không có vai trò ADMIN hoặc MANAGER -> chuyển hướng về `/home`.
     * 5. Nếu đủ điều kiện (là ADMIN hoặc MANAGER) -> trả về `true` cho phép request tiếp tục.
     * 
     * @param request HttpServletRequest đối tượng request tới.
     * @param response HttpServletResponse đối tượng response trả về.
     * @param handler Đối tượng xử lý (Controller method).
     * @return true nếu hợp lệ và được phép đi tiếp, false nếu bị chặn/chuyển hướng.
     * @throws Exception Các ngoại lệ xảy ra trong quá trình xử lý.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Nếu chưa đăng nhập, chuyển hướng về trang đăng nhập
        if (loggedInUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        // Nếu đã đăng nhập nhưng không phải là ADMIN hoặc MANAGER, chuyển hướng về trang chủ
        if (loggedInUser.getRole() != Role.ADMIN && loggedInUser.getRole() != Role.MANAGER) {
            response.sendRedirect(request.getContextPath() + "/home");
            return false;
        }

        // Cho phép truy cập nếu là ADMIN hoặc MANAGER
        return true;
    }
}

