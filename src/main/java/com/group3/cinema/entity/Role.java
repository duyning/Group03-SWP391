package com.group3.cinema.entity;

/**
 * Enum đại diện cho vai trò phân quyền (Role) người dùng trong hệ thống.
 * 
 * Các vai trò:
 * - ADMIN: Quản trị hệ thống cao nhất (Quản lý tài khoản, báo cáo tài chính, cấu hình hệ thống).
 * - MANAGER: Quản lý rạp (Quản lý phim, lịch chiếu, phòng chiếu, sản phẩm bắp nước).
 * - CUSTOMER: Khách hàng mua vé rạp trực tuyến.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
public enum Role {
    ADMIN,
    MANAGER,
    CUSTOMER
}
