package com.group3.cinema.repository;

import com.group3.cinema.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByPageOrderByDisplayOrderAscIdDesc(Banner.BannerPage page);

    List<Banner> findByPageAndActiveTrueOrderByDisplayOrderAscIdDesc(Banner.BannerPage page);

    Optional<Banner> findFirstByPageAndActiveTrueOrderByDisplayOrderAscIdDesc(Banner.BannerPage page);
}
