/**
 * Service phát sóng (Broadcast) gửi Thông báo tới tất cả Khách hàng đang hoạt động (`CustomerNotificationBroadcastService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi khi admin tạo phim mới, tin tức mới hoặc chiến dịch khuyến mãi mới.
 * - Các điểm gọi trực tiếp thường gặp:
 *   + `api.MovieController.createMovie(...)`: sau khi lưu phim mới thành công.
 *   + `PostController.savePost(...)`: sau khi lưu bài viết ở trạng thái PUBLISHED.
 *   + `PromotionController.broadcastPromotion(...)`: sau khi tạo/kích hoạt ưu đãi.
 * - Tương tác với:
 *   + `AccountRepository`: Lấy tất cả tài khoản `CUSTOMER` có `status = true` (`findByRoleAndStatusTrue`).
 *   + `NotificationService`: Tạo bản ghi `Notification` lưu vào hộp thư cho từng khách hàng.
 *
 * Lý do tách service này:
 * - Controller tạo phim/tin/khuyến mãi không cần biết cách tìm toàn bộ khách hàng.
 * - Nếu gửi lỗi cho một khách hàng, vòng lặp vẫn tiếp tục gửi cho khách khác và chỉ ghi log warn.
 * - NotificationService vẫn là nơi duy nhất chịu trách nhiệm tạo bản ghi Notification.
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
        /*
         * LUỒNG XỬ LÝ:
         * 1. Chuẩn hóa title/content để thông báo không bị rỗng trên giao diện.
         * 2. Lấy danh sách Account role CUSTOMER và status active.
         * 3. Với từng account, gọi NotificationService.sendNotification(...).
         * 4. NotificationService sẽ tự chuẩn hóa imageUrl/actionUrl và lưu vào bảng Notification.
         */
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

