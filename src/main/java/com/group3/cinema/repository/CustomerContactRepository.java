/**
 * Interface Repository quản lý các Yêu cầu liên hệ & Hỗ trợ từ Khách hàng (`customer_contacts`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CustomerContactService` phục vụ trang hỗ trợ liên hệ và quản lý yêu cầu liên hệ phía Admin.
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (25/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerContactRepository extends JpaRepository<CustomerContact, Long> {

    /**
     * Lấy tất cả các yêu cầu liên hệ của khách hàng, sắp xếp giảm dần theo thời gian gửi.
     */
    List<CustomerContact> findAllByOrderByCreatedAtDesc();

    /**
     * Lấy danh sách các yêu cầu liên hệ theo trạng thái xử lý (NEW, IN_PROGRESS, RESOLVED).
     */
    List<CustomerContact> findByStatusOrderByCreatedAtDesc(CustomerContact.ContactStatus status);

    /**
     * Đếm số lượng phiếu liên hệ theo trạng thái (dùng để đếm số phiếu đang chờ xử lý hiển thị badge).
     */
    long countByStatus(CustomerContact.ContactStatus status);
}

