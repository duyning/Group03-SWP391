/**
 * Record chứa thông tin hiển thị chi tiết của 1 suất chiếu phim sẵn có (BookingShowtimeView).
 * 
 * Được sử dụng bởi `BookingShowtimeService` để truyền dữ liệu thời gian chiếu, tên phòng, định dạng
 * và số lượng ghế trống tới giao diện đặt vé.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 * 
 * @param id ID của suất chiếu.
 * @param showDate Ngày chiếu.
 * @param startTime Giờ bắt đầu chiếu.
 * @param endTime Giờ kết thúc chiếu.
 * @param roomName Tên phòng chiếu (ví dụ: Phòng 01).
 * @param format Định dạng phim (2D, 3D, IMAX).
 * @param availableSeatCount Số lượng ghế còn trống chưa bị đặt hoặc giữ.
 */
package com.group3.cinema.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingShowtimeView(
        Long id,
        LocalDate showDate,
        LocalTime startTime,
        LocalTime endTime,
        String roomName,
        String format,
        int availableSeatCount
) {
}

