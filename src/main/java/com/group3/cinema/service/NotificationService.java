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
    private final AccountRepository accountRepository; // Giả sử cậu có AccountRepository

    public NotificationService(NotificationRepository notificationRepository, AccountRepository accountRepository) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
    }

    // 1. Hàm dùng để HỆ THỐNG GỬI THÔNG BÁO cho user (Ví dụ: gọi sau khi đặt vé thành công)
    @Transactional
    public void sendNotification(int accountId, String title, String content, NotificationType type) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));

        Notification notification = new Notification();
        notification.setAccount(account);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        // isRead và createdAt đã được tự động set trong @PrePersist ở Entity

        notificationRepository.save(notification);
    }

    // 2. Lấy danh sách thông báo có phân trang (dùng cho trang xem tất cả thông báo)
    public Page<Notification> getUserNotifications(int accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByAccount_AccountIDOrderByCreatedAtDesc(accountId, pageable);
    }

    // 3. Đếm số thông báo chưa đọc (để hiển thị số đỏ trên icon chuông)
    public int getUnreadCount(int accountId) {
        return notificationRepository.countByAccount_AccountIDAndIsReadFalse(accountId);
    }

    // 4. Đánh dấu 1 thông báo là đã đọc
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.markAsRead(notificationId);
    }

    // 5. Đánh dấu TẤT CẢ thông báo là đã đọc
    @Transactional
    public void markAllAsRead(int accountId) {
        notificationRepository.markAllAsRead(accountId);
    }
}