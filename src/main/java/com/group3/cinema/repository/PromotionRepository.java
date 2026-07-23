/**
 * Interface Repository quản lý thông tin các Chương trình Khuyến mãi công khai (`promotions`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PromotionService`, `PublicContentInitializer` để lọc danh sách khuyến mãi hiển thị cho khách hàng và trang quản lý Admin.
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (25/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    /**
     * Tìm kiếm bài viết chiến dịch khuyến mãi theo từ khóa, loại chiến dịch, nhóm đối tượng và trạng thái cho trang Admin.
     */
    @Query("SELECT p FROM Promotion p WHERE " +
            "(:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:type IS NULL OR p.type = :type) AND " +
            "(:targetGroup IS NULL OR p.targetGroup = :targetGroup) AND " +
            "(:status IS NULL OR p.status = :status) " +
            "ORDER BY p.startDate DESC, p.id DESC")
    List<Promotion> searchPromotions(@Param("keyword") String keyword,
                                     @Param("type") Promotion.CampaignType type,
                                     @Param("targetGroup") Promotion.TargetGroup targetGroup,
                                     @Param("status") Promotion.PromotionStatus status);

    /**
     * Lấy tất cả chiến dịch khuyến mãi công khai (`ACTIVE`) mà ngày kết thúc vẫn chưa trôi qua (`endDate >= today`).
     */
    List<Promotion> findByStatusAndEndDateGreaterThanEqualOrderByStartDateAscIdDesc(
            Promotion.PromotionStatus status,
            LocalDate today
    );

    /**
     * Tìm một chiến dịch khuyến mãi công khai theo ID và còn hạn sử dụng cho trang chi tiết khuyến mãi.
     */
    Optional<Promotion> findByIdAndStatusAndEndDateGreaterThanEqual(
            Long id,
            Promotion.PromotionStatus status,
            LocalDate today
    );

    /**
     * Đếm số lượng chiến dịch khuyến mãi đang hoạt động bị trùng khoảng thời gian (`startDate` -> `endDate`) cùng loại và đối tượng.
     */
    @Query("SELECT COUNT(p) FROM Promotion p WHERE " +
            "p.status = :status AND " +
            "p.type = :type AND " +
            "p.targetGroup = :targetGroup AND " +
            "(:currentId IS NULL OR p.id <> :currentId) AND " +
            "p.startDate <= :endDate AND p.endDate >= :startDate")
    long countOverlappingActiveCampaigns(@Param("type") Promotion.CampaignType type,
                                         @Param("targetGroup") Promotion.TargetGroup targetGroup,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate,
                                         @Param("status") Promotion.PromotionStatus status,
                                         @Param("currentId") Long currentId);

    /**
     * Kiểm tra xem tiêu đề chương trình khuyến mãi có bị trùng lặp hay không.
     */
    @Query("SELECT COUNT(p) > 0 FROM Promotion p WHERE " +
            "LOWER(p.title) = LOWER(:title) AND " +
            "(:currentId IS NULL OR p.id <> :currentId)")
    boolean existsDuplicateTitle(@Param("title") String title,
                                 @Param("currentId") Long currentId);
}

