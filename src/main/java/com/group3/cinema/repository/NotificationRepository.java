package com.group3.cinema.repository;

import com.group3.cinema.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Lấy danh sách thông báo của 1 Account (có phân trang)
    Page<Notification> findByAccount_AccountIDOrderByCreatedAtDesc(int accountId, Pageable pageable);

    // Đếm số lượng thông báo chưa đọc (để hiển thị badge đỏ trên icon chuông)
    int countByAccount_AccountIDAndIsReadFalse(int accountId);

    // Đánh dấu 1 thông báo là đã đọc
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :notificationId")
    void markAsRead(@Param("notificationId") Long notificationId);

    // Đánh dấu TẤT CẢ thông báo của 1 account là đã đọc
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.account.accountID = :accountId AND n.isRead = false")
    void markAllAsRead(@Param("accountId") int accountId);
}