package com.group3.cinema.config;

import com.group3.cinema.controller.BookingController;
import com.group3.cinema.service.SeatHoldingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor tự động giải phóng ghế đang giữ chỗ khi khách hàng di chuyển rời khỏi luồng đặt vé (quay lại Trang chủ, Danh sách phim, v.v.)
 */
@Component
public class SeatHoldReleaseInterceptor implements HandlerInterceptor {

    @Autowired
    private SeatHoldingService seatHoldingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        // Không giải phóng khi truy cập các URL thuộc luồng đặt vé (/booking/**) hoặc tài nguyên tĩnh
        if (uri.startsWith("/booking") || uri.startsWith("/css") || uri.startsWith("/js")
                || uri.startsWith("/images") || uri.startsWith("/uploads") || uri.startsWith("/api")
                || uri.startsWith("/payment")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            String token = (String) session.getAttribute("seatHoldToken");
            if (token != null && !token.isBlank()) {
                try {
                    seatHoldingService.releaseHold(token);
                } catch (Exception ignored) {
                }
                session.removeAttribute("seatHoldToken");
                session.removeAttribute("seatHoldExpiresAt");
                // Không xóa BOOKING_SELECTION_SESSION_KEY ở đây; nó sẽ tự bị ghi đè
                // khi khách hàng chọn suất chiếu mới trong lần đặt vé tiếp theo.
            }
        }
        return true;
    }
}
