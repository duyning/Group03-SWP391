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
// Long la kieu cua primary key ActivityLog.id.
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // Derived query: loc accountId va sap xep createdAt giam dan.
    List<ActivityLog> findByAccountIdOrderByCreatedAtDesc(Integer accountId);

    // JPQL tuong duong, co them Pageable de caller gioi han so dong.
    @Query("SELECT a FROM ActivityLog a WHERE a.accountId = :accountId ORDER BY a.createdAt DESC")
    // :accountId trong JPQL nhan gia tri tu tham so @Param.
    List<ActivityLog> findTopByAccountId(@Param("accountId") Integer accountId,
                                         // Pageable quy dinh page, size va co the ca sort.
                                         org.springframework.data.domain.Pageable pageable);
}
