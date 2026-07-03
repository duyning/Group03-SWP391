package com.group3.cinema.repository;

import com.group3.cinema.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByPageOrderByIdDesc(Banner.BannerPage page);

    List<Banner> findByPageAndActiveTrueOrderByIdDesc(Banner.BannerPage page);

    Optional<Banner> findFirstByPageAndActiveTrueOrderByIdDesc(Banner.BannerPage page);

    @Query("SELECT COUNT(b) > 0 FROM Banner b WHERE " +
            "LOWER(b.title) = LOWER(:title) AND " +
            "b.page = :page AND " +
            "(:currentId IS NULL OR b.id <> :currentId)")
    boolean existsDuplicateTitleInPage(@Param("title") String title,
                                       @Param("page") Banner.BannerPage page,
                                       @Param("currentId") Long currentId);
}
