package com.group3.cinema.config;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class RoleInterceptor implements HandlerInterceptor {

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
