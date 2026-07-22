/**
 * Interface Repository thao tác CSDL cho bảng quản lý Banner quảng cáo (`banners`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `BannerService` để lấy danh sách banner hiển thị tại Trang chủ (`HOME`) hoặc Trang tin tức (`NEWS`).
 * - Được dùng trong Admin Controller để kiểm tra trùng tiêu đề banner trong cùng vị trí trang (`existsDuplicateTitleInPage`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (09/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /**
     * Tìm tất cả banner thuộc về một vị trí trang chỉ định, sắp xếp giảm dần theo ID.
     */
    List<Banner> findByPageOrderByIdDesc(Banner.BannerPage page);

    /**
     * Tìm danh sách banner đang hoạt động (`active = true`) thuộc về trang chỉ định, sắp xếp giảm dần theo ID.
     */
    List<Banner> findByPageAndActiveTrueOrderByIdDesc(Banner.BannerPage page);

    /**
     * Lấy banner đầu tiên đang hoạt động (`active = true`) cho vị trí trang hiển thị hero.
     */
    Optional<Banner> findFirstByPageAndActiveTrueOrderByIdDesc(Banner.BannerPage page);

    /**
     * Kiểm tra xem tiêu đề banner đã tồn tại trong cùng vị trí trang chưa (bỏ qua bản ghi hiện tại `currentId` khi sửa).
     * 
     * @param title Tiêu đề banner cần kiểm tra.
     * @param page Trang vị trí hiển thị (HOME, NEWS).
     * @param currentId ID bản ghi hiện tại (hoặc null nếu tạo mới).
     * @return true nếu tiêu đề bị trùng.
     */
    @Query("SELECT COUNT(b) > 0 FROM Banner b WHERE " +
            "LOWER(b.title) = LOWER(:title) AND " +
            "b.page = :page AND " +
            "(:currentId IS NULL OR b.id <> :currentId)")
    boolean existsDuplicateTitleInPage(@Param("title") String title,
                                       @Param("page") Banner.BannerPage page,
                                       @Param("currentId") Long currentId);
}

