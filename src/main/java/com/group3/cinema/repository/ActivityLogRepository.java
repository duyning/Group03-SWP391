package com.group3.cinema.repository;

import com.group3.cinema.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho bảng activity_logs.
 *
 * Ngày thực hiện: 09/07/2026
 * Tạo bởi: DuongND_HE186619
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByAccountIdOrderByCreatedAtDesc(Integer accountId);

    @Query("SELECT a FROM ActivityLog a WHERE a.accountId = :accountId ORDER BY a.createdAt DESC")
    List<ActivityLog> findTopByAccountId(@Param("accountId") Integer accountId,
                                         org.springframework.data.domain.Pageable pageable);
}
