package com.group3.cinema.repository;

import com.group3.cinema.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Interface Repository quản lý dữ liệu cho Entity Notification (Thông báo hệ thống).
 * Hỗ trợ các thao tác truy vấn phân trang, đếm số thông báo chưa đọc và cập nhật trạng thái đã đọc.
 *
 * @author Group 3 - Cinema Management System
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Lấy danh sách thông báo cá nhân của một Tài khoản (Account) có hỗ trợ phân trang (Pagination).
     * Kết quả được sắp xếp giảm dần theo thời gian tạo (thông báo mới nhất nằm ở trên đầu).
     *
     * @param accountId ID của tài khoản người dùng
     * @param pageable Đối tượng cấu hình phân trang (trang hiện tại, số bản ghi/trang)
     * @return Trang danh sách các thông báo (Page<Notification>)
     */
    Page<Notification> findByAccount_AccountIDOrderByCreatedAtDesc(int accountId, Pageable pageable);

    /**
     * Đếm tổng số lượng thông báo chưa đọc (isRead = false) của một tài khoản.
     * Phục vụ hiển thị con số đếm trên huy hiệu (badge màu đỏ) tại biểu tượng Chuông thông báo trên thanh Header.
     *
     * @param accountId ID của tài khoản người dùng
     * @return Số lượng thông báo chưa đọc
     */
    int countByAccount_AccountIDAndIsReadFalse(int accountId);

    /**
     * Cập nhật trạng thái của 1 thông báo cụ thể thành "Đã đọc" (isRead = true).
     * Sử dụng @Modifying kết hợp @Query để thực thi câu lệnh UPDATE trực tiếp trong CSDL.
     *
     * @param notificationId ID của thông báo cần đánh dấu
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :notificationId")
    void markAsRead(@Param("notificationId") Long notificationId);

    /**
     * Đánh dấu TẤT CẢ các thông báo chưa đọc (isRead = false) của một tài khoản thành "Đã đọc" (isRead = true).
     * Phục vụ cho tính năng "Đánh dấu tất cả là đã đọc" trên Trung tâm thông báo.
     *
     * @param accountId ID của tài khoản người dùng
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.account.accountID = :accountId AND n.isRead = false")
    void markAllAsRead(@Param("accountId") int accountId);
}