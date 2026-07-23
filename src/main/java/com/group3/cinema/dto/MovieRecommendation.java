/**
 * Record gợi ý phim đề xuất cho khách hàng (MovieRecommendation).
 * 
 * Được tạo bởi `MovieRecommendationService` để tính toán và gợi ý các bộ phim tương tự
 * (cùng thể loại, cùng diễn viên hoặc đánh giá cao) hiển thị tại trang `movie-detail.html`.
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (10/07/2026)
 * 
 * @param movie Đối tượng Phim được đề xuất.
 * @param recommendationScore Thang điểm trùng khớp (%) (ví dụ: 85 -> 85%).
 * @param reason Lý do gợi ý bộ phim (ví dụ: "Cùng thể loại Kinh dị", "Có cùng diễn viên").
 */
package com.group3.cinema.dto;

import com.group3.cinema.entity.Movie;

public record MovieRecommendation(Movie movie, int recommendationScore, String reason) {
}

