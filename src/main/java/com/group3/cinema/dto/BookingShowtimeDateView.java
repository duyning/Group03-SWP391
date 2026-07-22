/**
 * Record nhóm danh sách các suất chiếu sẵn có theo từng ngày chiếu (BookingShowtimeDateView).
 * 
 * Sử dụng bởi `BookingShowtimeService` để nhóm các suất chiếu và trả về cho giao diện chọn suất chiếu (`booking-showtime.html`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (24/06/2026)
 * 
 * @param date Ngày chiếu (LocalDate).
 * @param dayOfWeekLabel Nhãn thứ trong tuần (ví dụ: "Thứ Hai", "Hôm nay", "Cuối tuần").
 * @param showtimes Danh sách các khung giờ chiếu sẵn có trong ngày.
 */
package com.group3.cinema.dto;

import java.time.LocalDate;
import java.util.List;

public record BookingShowtimeDateView(
        LocalDate date,
        String dayOfWeekLabel,
        List<BookingShowtimeView> showtimes
) {
}

