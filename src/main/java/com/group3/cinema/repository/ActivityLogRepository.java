/**
 * Interface Repository thao tác dữ liệu với nhật ký hoạt động hệ thống (`activity_logs`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ActivityLogService` để ghi nhận và truy vấn lịch sử hoạt động người dùng.
 * 
 * Khởi tạo bởi: DuongND_HE186619 (09/07/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Lấy toàn bộ nhật ký hoạt động của tài khoản chỉ định, sắp xếp giảm dần theo thời gian tạo.
     * 
     * @param accountId ID tài khoản người dùng.
     * @return Danh sách ActivityLog.
     */
    List<ActivityLog> findByAccountIdOrderByCreatedAtDesc(Integer accountId);

    /**
     * Lấy danh sách nhật ký hoạt động mới nhất có phân trang của một tài khoản.
     * 
     * @param accountId ID tài khoản.
     * @param pageable Đối tượng phân trang Pageable (ví dụ PageRequest.of(0, 10)).
     * @return Danh sách nhật ký trang chỉ định.
     */
    @Query("SELECT a FROM ActivityLog a WHERE a.accountId = :accountId ORDER BY a.createdAt DESC")
    List<ActivityLog> findTopByAccountId(@Param("accountId") Integer accountId,
                                         org.springframework.data.domain.Pageable pageable);
}

