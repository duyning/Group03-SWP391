/**
 * Record lưu trữ thông tin suất chiếu người dùng đang chọn trong phiên làm việc (Session) (BookingSelection).
 * 
 * Truyền qua lại giữa các bước đặt vé: Chọn suất chiếu -> Chọn ghế -> Chọn Combo -> Thanh toán.
 * Triển khai `Serializable` để lưu an toàn vào `HttpSession`.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 * 
 * @param showtimeId ID suất chiếu đã chọn.
 * @param movieId ID bộ phim.
 * @param roomId ID phòng chiếu.
 * @param movieTitle Tên bộ phim.
 * @param roomName Tên phòng chiếu.
 * @param showDate Ngày chiếu.
 * @param startTime Giờ bắt đầu chiếu.
 * @param endTime Giờ kết thúc chiếu.
 * @param format Định dạng phim (2D, 3D, IMAX).
 */
package com.group3.cinema.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingSelection(
        Long showtimeId,
        Integer movieId,
        Long roomId,
        String movieTitle,
        String roomName,
        LocalDate showDate,
        LocalTime startTime,
        LocalTime endTime,
        String format
) implements Serializable {
}

