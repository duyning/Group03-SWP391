package com.group3.cinema.service;

import com.group3.cinema.entity.ActivityLog;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.repository.ActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service xử lý ghi và truy vấn nhật ký hoạt động của người dùng.
 *
 * Ngày thực hiện: 09/07/2026
 * Tạo bởi: DuongND_HE186619
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Ghi một bản ghi nhật ký hoạt động.
     */
    public void log(Integer accountId, ActionType action, String description) {
        ActivityLog log = new ActivityLog(accountId, action, description);
        activityLogRepository.save(log);
    }

    /**
     * Ghi nhật ký kèm địa chỉ IP của người dùng.
     */
    public void log(Integer accountId, ActionType action, String description, HttpServletRequest request) {
        String ip = extractIp(request);
        ActivityLog log = new ActivityLog(accountId, action, description, ip);
        activityLogRepository.save(log);
    }

    /**
     * Lấy toàn bộ nhật ký hoạt động của một tài khoản, mới nhất lên đầu.
     */
    public List<ActivityLog> getLogsForAccount(Integer accountId) {
        return activityLogRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For có thể chứa nhiều IP, lấy IP đầu tiên
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
