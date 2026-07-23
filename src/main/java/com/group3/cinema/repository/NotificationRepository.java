/**
 * Interface Repository quản lý các Thông báo gửi tới tài khoản người dùng (`notification`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `NotificationService` để gửi thông báo biến động đơn hàng/vé, đánh dấu đã đọc (`markAsRead`, `markAllAsRead`)
 *   và đếm số thông báo chưa đọc (`countByAccount_AccountIDAndIsReadFalse`) hiển thị badge đỏ icon chuông.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Lấy danh sách thông báo của một tài khoản có phân trang, sắp xếp giảm dần theo thời gian tạo.
     */
    Page<Notification> findByAccount_AccountIDOrderByCreatedAtDesc(int accountId, Pageable pageable);

    /**
     * Đếm số lượng thông báo chưa đọc (`isRead = false`) của tài khoản để hiển thị badge đỏ thông báo.
     */
    int countByAccount_AccountIDAndIsReadFalse(int accountId);

    /**
     * Đánh dấu 1 thông báo cụ thể là đã đọc (`isRead = true`).
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :notificationId")
    void markAsRead(@Param("notificationId") Long notificationId);

    /**
     * Đánh dấu tất cả thông báo chưa đọc của tài khoản thành đã đọc (`isRead = true`).
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.account.accountID = :accountId AND n.isRead = false")
    void markAllAsRead(@Param("accountId") int accountId);
}