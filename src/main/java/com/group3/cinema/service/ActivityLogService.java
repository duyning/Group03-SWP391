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
// Spring tao mot bean service dung chung cho cac controller can ghi/doc log.
public class ActivityLogService {

    // final dam bao repository duoc gan mot lan qua constructor.
    private final ActivityLogRepository activityLogRepository;

    // Constructor injection giup service de test va khong phu thuoc field injection.
    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        // Luu repository duoc Spring inject vao field.
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Ghi một bản ghi nhật ký hoạt động.
     */
    public void log(Integer accountId, ActionType action, String description) {
        // Tao entity log; constructor tu gan createdAt la thoi diem hien tai.
        ActivityLog log = new ActivityLog(accountId, action, description);
        // INSERT ban ghi vao bang activity_logs.
        activityLogRepository.save(log);
    }

    /**
     * Ghi nhật ký kèm địa chỉ IP của người dùng.
     */
    public void log(Integer accountId, ActionType action, String description, HttpServletRequest request) {
        // Lay IP that cua client tu request/proxy header.
        String ip = extractIp(request);
        // Tao entity log co them dia chi IP.
        ActivityLog log = new ActivityLog(accountId, action, description, ip);
        // INSERT log vao database.
        activityLogRepository.save(log);
    }

    /**
     * Lấy toàn bộ nhật ký hoạt động của một tài khoản, mới nhất lên đầu.
     */
    public List<ActivityLog> getLogsForAccount(Integer accountId) {
        // Derived query loc theo accountId va sap xep createdAt giam dan.
        return activityLogRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    // Ham noi bo lay dia chi IP tu request.
    private String extractIp(HttpServletRequest request) {
        // X-Forwarded-For thuong duoc reverse proxy gan IP goc cua client.
        String ip = request.getHeader("X-Forwarded-For");
        // Neu khong di qua proxy hoac header khong hop le, dung remote address.
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For có thể chứa nhiều IP, lấy IP đầu tiên
        if (ip != null && ip.contains(",")) {
            // Header co the la chuoi nhieu IP; phan tu dau tien la client goc.
            ip = ip.split(",")[0].trim();
        }
        // Tra IP da chuan hoa de ghi vao entity.
        return ip;
    }
}
