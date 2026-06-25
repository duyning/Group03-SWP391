package com.group3.cinema.repository;

/*
 * Created on 2026-06-25: Repository for public promotion campaign management.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

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

    List<Promotion> findByStatusAndEndDateGreaterThanEqualOrderByStartDateAscIdDesc(
            Promotion.PromotionStatus status,
            LocalDate today
    );

    Optional<Promotion> findByIdAndStatusAndEndDateGreaterThanEqual(
            Long id,
            Promotion.PromotionStatus status,
            LocalDate today
    );

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

    @Query("SELECT COUNT(p) > 0 FROM Promotion p WHERE " +
            "LOWER(p.title) = LOWER(:title) AND " +
            "(:currentId IS NULL OR p.id <> :currentId)")
    boolean existsDuplicateTitle(@Param("title") String title,
                                 @Param("currentId") Long currentId);
}
