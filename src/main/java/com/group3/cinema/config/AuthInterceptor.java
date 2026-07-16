package com.group3.cinema.config;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AuthInterceptor implements HandlerInterceptor {

    private final List<Role> allowedRoles;

    public AuthInterceptor() {
        this.allowedRoles = null;
    }

    public AuthInterceptor(Role... allowedRoles) {
        this.allowedRoles = Arrays.asList(allowedRoles);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            if (expectsApiResponse(request)) {
                writeApiError(response, HttpServletResponse.SC_UNAUTHORIZED, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
                return false;
            }
            session = request.getSession(true);
            String requestedUrl = request.getRequestURI();
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                requestedUrl += "?" + request.getQueryString();
            }
            session.setAttribute("redirectAfterLogin", requestedUrl);
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }

        Account account = (Account) session.getAttribute("loggedInUser");
        if (account.getRole() != null && allowedRoles.contains(account.getRole())) {
            return true;
        }

        if (expectsApiResponse(request)) {
            writeApiError(response, HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
            return false;
        }
        response.sendRedirect(request.getContextPath() + "/access-denied");
        return false;
    }

    private boolean expectsApiResponse(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return uri.startsWith(request.getContextPath() + "/api/")
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.toLowerCase().contains("application/json"));
    }

    private void writeApiError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
