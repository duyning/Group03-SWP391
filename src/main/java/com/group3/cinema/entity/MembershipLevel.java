/**
 * Enum đại diện cho các cấp độ hạng thành viên (Membership Level) của tài khoản khách hàng.
 * 
 * Các hạng: BRONZE (Đồng), SILVER (Bạc), GOLD (Vàng), PLAT (Bạch kim).
 * Dùng để tính điểm tích lũy và hưởng chính sách giảm giá vé/ưu đãi bắp nước tương ứng.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
package com.group3.cinema.entity;

public enum MembershipLevel {
    // Hang Dong: duoi 1.000 diem.
    BRONZE,
    // Hang Bac: tu 1.000 den duoi 5.000 diem.
    SILVER,
    // Hang Vang: tu 5.000 diem tro len.
    GOLD,
    // Gia tri du lieu cu; ProfileController hien thi tuong duong hang Vang.
    PLAT
}

