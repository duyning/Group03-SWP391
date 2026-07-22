/**
 * Service phát sóng (Broadcast) gửi Thông báo tới tất cả Khách hàng đang hoạt động (`CustomerNotificationBroadcastService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi khi tạo bài viết khuyến mãi mới (`PromotionService`), phim mới sắp ra mắt (`MovieService`) hoặc sự kiện hệ thống.
 * - Tương tác với:
 *   + `AccountRepository`: Lấy tất cả tài khoản `CUSTOMER` có `status = true` (`findByRoleAndStatusTrue`).
 *   + `NotificationService`: Tạo bản ghi `Notification` lưu vào hộp thư cho từng khách hàng.
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.entity.Role;
import com.group3.cinema.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerNotificationBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotificationBroadcastService.class);

    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    public CustomerNotificationBroadcastService(AccountRepository accountRepository,
                                                NotificationService notificationService) {
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    /**
     * Gửi thông báo tới toàn bộ tài khoản Khách hàng active.
     */
    public void sendToActiveCustomers(String title, String content, NotificationType type) {
        sendToActiveCustomers(title, content, type, null, null);
    }

    /**
     * Gửi thông báo có đính kèm ảnh và liên kết hành động tới toàn bộ tài khoản Khách hàng active.
     * 
     * @param title Tiêu đề thông báo.
     * @param content Nội dung thông báo.
     * @param type Phân loại thông báo (PROMOTION, SYSTEM, MOVIE...).
     * @param imageUrl Đường dẫn ảnh banner đi kèm.
     * @param actionUrl Đường dẫn URL khi nhấn vào thông báo.
     */
    public void sendToActiveCustomers(String title, String content, NotificationType type,
                                      String imageUrl, String actionUrl) {
        String safeTitle = title == null || title.isBlank() ? "Thông báo mới từ rạp" : title.trim();
        String safeContent = content == null || content.isBlank()
                ? "Bạn có nội dung mới cần quan tâm trên hệ thống."
                : content.trim();
        List<Account> customers;
        try {
            customers = accountRepository.findByRoleAndStatusTrue(Role.CUSTOMER);
        } catch (RuntimeException exception) {
            log.warn("Không thể tải danh sách khách hàng để gửi thông báo '{}': {}",
                    safeTitle, exception.getMessage());
            return;
        }
        for (Account account : customers) {
            try {
                notificationService.sendNotification(account.getAccountID(), safeTitle, safeContent, type, imageUrl, actionUrl);
            } catch (RuntimeException exception) {
                log.warn("Không thể gửi thông báo '{}' cho account {}: {}",
                        safeTitle, account.getAccountID(), exception.getMessage());
            }
        }
    }
}

