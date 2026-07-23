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

/**
 * Service quản lý toàn bộ hệ thống Thông báo (Notification Management).
 * Đảm nhận nhiệm vụ gửi thông báo tự động (đặt vé thành công, khuyến mãi...),
 * đếm số thông báo chưa đọc, đánh dấu đã đọc và kiểm tra an toàn đường dẫn chuyển hướng (URL Security).
 *
 * @author Group 3 - Cinema Management System
 */
@Service
public class NotificationService {

    /** Repository thao tác dữ liệu với bảng Notification trong CSDL */
    private final NotificationRepository notificationRepository;

    /** Repository truy vấn thông tin Tài khoản người dùng */
    private final AccountRepository accountRepository; // Giả sử cậu có AccountRepository

    /**
     * Constructor Injection để tiêm các phụ thuộc Repository cần thiết.
     *
     * @param notificationRepository Repository quản lý thông báo
     * @param accountRepository Repository quản lý tài khoản
     */
    public NotificationService(NotificationRepository notificationRepository, AccountRepository accountRepository) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
    }

    // 1. Hàm dùng để HỆ THỐNG GỬI THÔNG BÁO cho user (Ví dụ: gọi sau khi đặt vé thành công)
    /**
     * Gửi thông báo đơn giản (không bao gồm hình ảnh và đường dẫn đính kèm).
     * Overloaded method gọi sang hàm gửi thông báo đầy đủ với tham số mặc định là null.
     *
     * @param accountId ID của tài khoản nhận thông báo
     * @param title Tiêu đề thông báo
     * @param content Nội dung chi tiết thông báo
     * @param type Loại thông báo (Enum: SYSTEM, BOOKING, PROMOTION, v.v.)
     */
    @Transactional
    public void sendNotification(int accountId, String title, String content, NotificationType type) {
        sendNotification(accountId, title, content, type, null, null);
    }

    /**
     * Gửi thông báo đầy đủ thông tin cho người dùng.
     * Tự động kiểm tra sự tồn tại của Tài khoản, chuẩn hóa URL hình ảnh và URL hành động chuyển hướng.
     *
     * @param accountId ID của tài khoản nhận thông báo
     * @param title Tiêu đề thông báo
     * @param content Nội dung chi tiết thông báo
     * @param type Loại thông báo
     * @param imageUrl Đường dẫn ảnh đính kèm (cho phép null/rỗng)
     * @param actionUrl Đường dẫn chuyển hướng khi người dùng nhấn vào thông báo (cho phép null/rỗng)
     * @throws IllegalArgumentException nếu không tìm thấy tài khoản tương ứng
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
        // isRead và createdAt đã được tự động set trong @PrePersist ở Entity

        notificationRepository.save(notification);
    }

    // 2. Lấy danh sách thông báo có phân trang (dùng cho trang xem tất cả thông báo)
    /**
     * Lấy danh sách thông báo cá nhân của một tài khoản theo cơ chế Phân trang (Pagination).
     *
     * @param accountId ID của tài khoản người dùng
     * @param page Số trang hiện tại (bắt đầu từ 0)
     * @param size Số lượng bản ghi trên mỗi trang
     * @return Trang danh sách thông báo (Page<Notification>) sắp xếp mới nhất lên đầu
     */
    public Page<Notification> getUserNotifications(int accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByAccount_AccountIDOrderByCreatedAtDesc(accountId, pageable);
    }

    // 3. Đếm số thông báo chưa đọc (để hiển thị số đỏ trên icon chuông)
    /**
     * Lấy tổng số lượng thông báo chưa đọc của người dùng.
     * Phục vụ hiển thị con số trên biểu tượng Chuông thông báo (Bell Badge Icon) ở Header.
     *
     * @param accountId ID của tài khoản người dùng
     * @return Số lượng thông báo có trạng thái isRead = false
     */
    public int getUnreadCount(int accountId) {
        return notificationRepository.countByAccount_AccountIDAndIsReadFalse(accountId);
    }

    // 4. Đánh dấu 1 thông báo là đã đọc
    /**
     * Đánh dấu một thông báo cụ thể thành trạng thái "Đã đọc".
     *
     * @param notificationId ID của thông báo cần cập nhật
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.markAsRead(notificationId);
    }

    // 5. Đánh dấu TẤT CẢ thông báo là đã đọc
    /**
     * Đánh dấu tất cả thông báo thuộc về một tài khoản thành trạng thái "Đã đọc".
     *
     * @param accountId ID của tài khoản người dùng
     */
    @Transactional
    public void markAllAsRead(int accountId) {
        notificationRepository.markAllAsRead(accountId);
    }

    /**
     * Lấy đường dẫn chuyển hướng (Action URL) an toàn của một thông báo.
     * Đảm bảo tính bảo mật (Chống IDOR / Phân quyền): Chỉ cho phép lấy URL nếu thông báo đó thực sự thuộc về accountId đang đăng nhập.
     *
     * @param notificationId ID của thông báo
     * @param accountId ID của tài khoản đang thực hiện Yêu cầu
     * @return Đường dẫn nội bộ hợp lệ hoặc null nếu không khớp quyền sở hữu / URL không hợp lệ
     */
    public String getActionUrl(Long notificationId, int accountId) {
        return notificationRepository.findById(notificationId)
                .filter(notification -> notification.getAccount() != null
                        && notification.getAccount().getAccountID() == accountId)
                .map(Notification::getActionUrl)
                .map(this::normalizeInternalUrl)
                .orElse(null);
    }

    /**
     * Helper Method: Chuẩn hóa chuỗi URL tùy chọn. Cắt khoảng trắng thừa hai đầu và trả về null nếu chuỗi rỗng.
     *
     * @param url Chuỗi URL đầu vào
     * @return Chuỗi đã chuẩn hóa hoặc null
     */
    private String normalizeOptionalUrl(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }

    /**
     * Helper Method: Chuẩn hóa và kiểm tra an toàn đường dẫn nội bộ (Chống lỗ hổng Open Redirect).
     * Chỉ chấp nhận các đường dẫn bắt đầu bằng dấu `/` (Path nội bộ) và chặn các đường dẫn nguy hiểm như `//` hoặc URL ngoài (`http://`).
     *
     * @param url Chuỗi đường dẫn cần kiểm tra
     * @return Đường dẫn hợp lệ hoặc null nếu vi phạm quy tắc an toàn
     */
    private String normalizeInternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim();
        return normalized.startsWith("/") && !normalized.startsWith("//") ? normalized : null;
    }
}