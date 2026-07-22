/**
 * Service xử lý ghi nhận và truy vấn Nhật ký hoạt động người dùng (`ActivityLogService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `AccountController`, `CustomerBookingService`, `VoucherService`, `CustomerController`.
 * - Tương tác với `ActivityLogRepository` để ghi nhận sự kiện (`save`) và truy vấn danh sách (`findByAccountIdOrderByCreatedAtDesc`).
 * 
 * Ngày thực hiện: 09/07/2026
 * Tạo bởi: DuongND_HE186619
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.ActivityLog;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.repository.ActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Ghi nhận một hành động của tài khoản vào nhật ký hệ thống.
     * 
     * @param accountId ID tài khoản thực hiện.
     * @param action Loại hành động (`ActionType`: LOGIN, BOOKING, CHANGE_PASSWORD...).
     * @param description Mô tả chi tiết hành động.
     */
    public void log(Integer accountId, ActionType action, String description) {
        ActivityLog log = new ActivityLog(accountId, action, description);
        activityLogRepository.save(log);
    }

    /**
     * Ghi nhận hành động kèm địa chỉ IP trích xuất từ HttpServletRequest.
     * 
     * @param accountId ID tài khoản.
     * @param action Loại hành động.
     * @param description Mô tả hành động.
     * @param request HttpServletRequest để lấy IP client.
     */
    public void log(Integer accountId, ActionType action, String description, HttpServletRequest request) {
        String ip = extractIp(request);
        ActivityLog log = new ActivityLog(accountId, action, description, ip);
        activityLogRepository.save(log);
    }

    /**
     * Lấy toàn bộ nhật ký hoạt động của một tài khoản, sắp xếp mới nhất lên đầu.
     * 
     * @param accountId ID tài khoản.
     * @return Danh sách các bản ghi ActivityLog.
     */
    public List<ActivityLog> getLogsForAccount(Integer accountId) {
        return activityLogRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /**
     * Trích xuất địa chỉ IP thực của Client từ HttpServletRequest (xử lý qua proxy/load balancer).
     */
    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

