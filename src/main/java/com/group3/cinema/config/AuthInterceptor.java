/**
 * Bộ chặn xác thực người dùng (AuthInterceptor).
 * 
 * Chức năng:
 * 1. Kiểm tra session đăng nhập (`loggedInUser`).
 * 2. Lưu lại URL trước đó vào session (`redirectAfterLogin`) để chuyển hướng lại sau khi đăng nhập thành công.
 * 3. Phân biệt request giữa Giao diện Web (HTML) và AJAX / REST API (JSON response).
 * 4. Kiểm tra danh sách vai trò cho phép (`allowedRoles`).
 */
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
    private final String accessDeniedRedirect;
    private final String accessDeniedMessage;

    /**
     * Khởi tạo mặc định: Yêu cầu đăng nhập, không giới hạn vai trò cụ thể.
     */
    public AuthInterceptor() {
        this.allowedRoles = null;
        this.accessDeniedRedirect = "/access-denied";
        this.accessDeniedMessage = null;
    }

    /**
     * Khởi tạo với danh sách vai trò được phép truy cập.
     * 
     * @param allowedRoles Các vai trò (Role.ADMIN, Role.MANAGER, Role.CUSTOMER) được phép.
     */
    public AuthInterceptor(Role... allowedRoles) {
        this.allowedRoles = Arrays.asList(allowedRoles);
        this.accessDeniedRedirect = "/access-denied";
        this.accessDeniedMessage = null;
    }

    /**
     * Khởi tạo với trang chuyển hướng tùy chỉnh và thông báo lỗi.
     * 
     * @param accessDeniedRedirect URL trang chuyển hướng khi không có quyền.
     * @param accessDeniedMessage Thông báo lỗi sẽ lưu vào Session.
     * @param allowedRoles Các vai trò được phép.
     */
    public AuthInterceptor(String accessDeniedRedirect, String accessDeniedMessage, Role... allowedRoles) {
        this.allowedRoles = Arrays.asList(allowedRoles);
        this.accessDeniedRedirect = accessDeniedRedirect;
        this.accessDeniedMessage = accessDeniedMessage;
    }

    /**
     * Phương thức xử lý chính trước khi gọi Controller.
     * 
     * Quy trình:
     * 1. Lấy HttpSession từ request (`request.getSession(false)`).
     * 2. Nếu session null hoặc không có `loggedInUser`:
     *    - Nếu request là API (gọi `expectsApiResponse(request)`): Ghi phản hồi lỗi 401 JSON qua `writeApiError()`.
     *    - Nếu request là Web: Lưu URL hiện tại vào session (`redirectAfterLogin`) và chuyển hướng về `/login`.
     * 3. Nếu người dùng đã đăng nhập:
     *    - Kiểm tra nếu `allowedRoles` trống -> cho phép truy cập (trả về `true`).
     *    - Kiểm tra vai trò của tài khoản có nằm trong `allowedRoles` hay không.
     *    - Nếu không hợp lệ: Trả về lỗi 403 JSON cho API hoặc chuyển hướng về `accessDeniedRedirect`.
     * 
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param handler Đối tượng handler
     * @return true nếu cho phép tiếp tục, false nếu bị từ chối
     */
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
        if (accessDeniedMessage != null && !accessDeniedMessage.isBlank()) {
            session.setAttribute("errorMessage", accessDeniedMessage);
        }
        response.sendRedirect(request.getContextPath() + accessDeniedRedirect);
        return false;
    }

    /**
     * Kiểm tra xem request có yêu cầu kết quả trả về dạng JSON/API hay không.
     * 
     * Đọc thông tin đường dẫn URI xuất phát từ `/api/`, hoặc kiểm tra header `Accept` chứa `application/json`,
     * hoặc `X-Requested-With` bằng `XMLHttpRequest`.
     * 
     * @param request HttpServletRequest
     * @return true nếu là yêu cầu API, false nếu là trang web HTML
     */
    private boolean expectsApiResponse(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return uri.startsWith(request.getContextPath() + "/api/")
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.toLowerCase().contains("application/json"));
    }

    /**
     * Ghi kết quả phản hồi lỗi dưới dạng chuỗi JSON trực tiếp vào HttpServletResponse cho REST API.
     * 
     * @param response HttpServletResponse đối tượng response.
     * @param status Mã trạng thái HTTP (401 Unauthorized, 403 Forbidden).
     * @param message Thông báo lỗi.
     * @throws IOException Ngoại lệ khi ghi dữ liệu ra response stream.
     */
    private void writeApiError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + escapeJson(message) + "\"}");
    }

    /**
     * Escape chuỗi JSON tránh lỗi định dạng JSON do dấu ngoặc kép hoặc gạch chéo ngược.
     * 
     * @param value Chuỗi gốc.
     * @return Chuỗi đã mã hóa an toàn cho JSON.
     */
    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}

