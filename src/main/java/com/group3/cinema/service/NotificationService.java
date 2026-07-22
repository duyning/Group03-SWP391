/**
 * Service quản lý gửi và xử lý Hộp thư thông báo riêng cho từng Khách hàng (`NotificationService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `NotificationController`, `CustomerBookingService`, `CustomerNotificationBroadcastService`, `PaymentService`.
 * - Tương tác với:
 *   + `NotificationRepository`: Lưu thông báo (`save`), đếm thông báo chưa đọc (`countByAccount_AccountIDAndIsReadFalse`), đánh dấu đã đọc (`markAsRead`, `markAllAsRead`).
 *   + `AccountRepository`: Lấy tài khoản người nhận (`findById`).
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Notification;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;

    public NotificationService(NotificationRepository notificationRepository, AccountRepository accountRepository) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Gửi thông báo hệ thống cho tài khoản chỉ định.
     */
    @Transactional
    public void sendNotification(int accountId, String title, String content, NotificationType type) {
        sendNotification(accountId, title, content, type, null, null);
    }

    /**
     * Gửi thông báo đính kèm hình ảnh và URL điều hướng khi nhấn vào thông báo.
     * 
     * @param accountId ID tài khoản nhận.
     * @param title Tiêu đề.
     * @param content Nội dung thông báo.
     * @param type Phân loại thông báo (`BOOKING`, `PROMOTION`, `SYSTEM`).
     * @param imageUrl Đường dẫn ảnh đại diện.
     * @param actionUrl Đường dẫn chuyển hướng nội bộ.
     */
    @Transactional
    public void sendNotification(int accountId, String title, String content, NotificationType type,
                                  String imageUrl, String actionUrl) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));

        Notification notification = new Notification();
        notification.setAccount(account);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setImageUrl(normalizeOptionalUrl(imageUrl));
        notification.setActionUrl(normalizeInternalUrl(actionUrl));

        notificationRepository.save(notification);
    }

    /**
     * Lấy danh sách các thông báo cá nhân có phân trang (sắp xếp mới nhất lên đầu).
     */
    public Page<Notification> getUserNotifications(int accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByAccount_AccountIDOrderByCreatedAtDesc(accountId, pageable);
    }

    /**
     * Đếm tổng số lượng thông báo chưa đọc của tài khoản (hiển thị Badge đỏ góc màn hình).
     */
    public int getUnreadCount(int accountId) {
        return notificationRepository.countByAccount_AccountIDAndIsReadFalse(accountId);
    }

    /**
     * Đánh dấu 1 thông báo cụ thể là đã đọc (`isRead = true`).
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.markAsRead(notificationId);
    }

    /**
     * Đánh dấu tất cả thông báo của tài khoản là đã đọc.
     */
    @Transactional
    public void markAllAsRead(int accountId) {
        notificationRepository.markAllAsRead(accountId);
    }

    /** Lấy URL điều hướng khi người dùng nhấp vào dòng thông báo. */
    public String getActionUrl(Long notificationId, int accountId) {
        return notificationRepository.findById(notificationId)
                .filter(notification -> notification.getAccount() != null
                        && notification.getAccount().getAccountID() == accountId)
                .map(Notification::getActionUrl)
                .map(this::normalizeInternalUrl)
                .orElse(null);
    }

    private String normalizeOptionalUrl(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }

    private String normalizeInternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim();
        return normalized.startsWith("/") && !normalized.startsWith("//") ? normalized : null;
    }
}

