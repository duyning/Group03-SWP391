/**
 * Interface Repository thao tác bảng dữ liệu gợi ý Đạo diễn, Nhà sản xuất, Diễn viên (`movie_person_suggestions`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `MovieService` để cung cấp API autocomplete gợi ý nhân sự phim khi Admin tạo/sửa phim.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.MoviePersonSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoviePersonSuggestionRepository extends JpaRepository<MoviePersonSuggestion, Integer> {

    /**
     * Lấy danh sách gợi ý nhân sự phim theo loại (DIRECTOR, PRODUCER, ACTOR).
     */
    List<MoviePersonSuggestion> findByTypeIgnoreCase(String type);

    /**
     * Tìm kiếm bản ghi nhân sự phim theo tên và loại (không phân biệt chữ hoa/thường).
     */
    Optional<MoviePersonSuggestion> findByNameIgnoreCaseAndTypeIgnoreCase(String name, String type);
}

