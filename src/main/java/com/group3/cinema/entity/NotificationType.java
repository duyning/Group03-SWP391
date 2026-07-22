/**
 * Enum phân loại các loại thông báo gửi tới người dùng trong hệ thống rạp xem phim.
 * 
 * Các loại:
 * - BOOKING: Đặt vé thành công/thay đổi trạng thái giữ ghế.
 * - PAYMENT: Thanh toán thành công/thất bại.
 * - PROMOTION: Chương trình khuyến mãi mới.
 * - VOUCHER: Tặng voucher/voucher sắp hết hạn.
 * - MOVIE: Phim mới ra mắt/lịch chiếu mới.
 * - NEWS: Tin tức điện ảnh.
 * - SYSTEM: Thông báo bảo trì/thông báo từ hệ thống.
 */
package com.group3.cinema.entity;

public enum NotificationType {
    BOOKING,
    PAYMENT,
    PROMOTION,
    VOUCHER,
    MOVIE,
    NEWS,
    SYSTEM
}

