/**
 * Record dữ liệu thông tin chi tiết của 1 ghế hiển thị trên sơ đồ chọn ghế (BookingSeatView).
 * 
 * Được tạo bởi `BookingShowtimeService` / `CustomerBookingService` và trả về cho client AJAX
 * hoặc Thymeleaf render sơ đồ ghế (`seat-selection.html`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 * 
 * @param id ID của ghế trong CSDL.
 * @param rowIndex Chỉ số hàng (Row index 0-indexed).
 * @param colIndex Chỉ số cột (Column index 0-indexed).
 * @param label Tên nhãn ghế hiển thị (ví dụ: A1, B5, D10).
 * @param type Mã loại ghế (std: Thường, vip: VIP, couple: Đôi, disabled: Ghế hỏng/Lối đi).
 * @param displayName Tên loại ghế hiển thị góc nhìn người dùng.
 * @param color Mã màu đại diện cho loại ghế.
 * @param capacity Sức chứa (1 đối với ghế đơn, 2 đối với ghế đôi).
 * @param sellable Trạng thái có thể bán hay không.
 * @param status Trạng thái ghế hiện tại (AVAILABLE: Trống, HELD: Đang bị giữ tạm, BOOKED: Đã bán).
 * @param price Giá vé tính toán cho ghế này tại suất chiếu hiện tại.
 */
package com.group3.cinema.dto;

import java.math.BigDecimal;

public record BookingSeatView(Long id,
                              int rowIndex,
                              int colIndex,
                              String label,
                              String type,
                              String displayName,
                              String color,
                              int capacity,
                              boolean sellable,
                              String status,
                              BigDecimal price) { }

