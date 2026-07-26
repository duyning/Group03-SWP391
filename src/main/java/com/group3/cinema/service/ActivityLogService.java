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
     * Ghi nhận một hành động của tài khoản vào nhật ký hệ thống.
     * 
     * @param accountId ID tài khoản thực hiện.
     * @param action Loại hành động (`ActionType`: LOGIN, BOOKING, CHANGE_PASSWORD...).
     * @param description Mô tả chi tiết hành động.
     */
    public void log(Integer accountId, ActionType action, String description) {
        // Tao entity log; constructor tu gan createdAt la thoi diem hien tai.
        ActivityLog log = new ActivityLog(accountId, action, description);
        // INSERT ban ghi vao bang activity_logs.
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
        // Lay IP that cua client tu request/proxy header.
        String ip = extractIp(request);
        // Tao entity log co them dia chi IP.
        ActivityLog log = new ActivityLog(accountId, action, description, ip);
        // INSERT log vao database.
        activityLogRepository.save(log);
    }

    /**
     * Lấy toàn bộ nhật ký hoạt động của một tài khoản, sắp xếp mới nhất lên đầu.
     * 
     * @param accountId ID tài khoản.
     * @return Danh sách các bản ghi ActivityLog.
     */
    public List<ActivityLog> getLogsForAccount(Integer accountId) {
        // Derived query loc theo accountId va sap xep createdAt giam dan.
        return activityLogRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /**
     * Trích xuất địa chỉ IP thực của Client từ HttpServletRequest (xử lý qua proxy/load balancer).
     */
    private String extractIp(HttpServletRequest request) {
        // X-Forwarded-For thuong duoc reverse proxy gan IP goc cua client.
        String ip = request.getHeader("X-Forwarded-For");
        // Neu khong di qua proxy hoac header khong hop le, dung remote address.
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            // Header co the la chuoi nhieu IP; phan tu dau tien la client goc.
            ip = ip.split(",")[0].trim();
        }
        // Tra IP da chuan hoa de ghi vao entity.
        return ip;
    }
}

