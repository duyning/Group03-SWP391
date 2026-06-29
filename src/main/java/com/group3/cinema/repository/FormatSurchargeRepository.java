/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: FormatSurchargeRepository.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Khai báo repository cho FormatSurcharge.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.FormatSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormatSurchargeRepository extends JpaRepository<FormatSurcharge, Long> {
    Optional<FormatSurcharge> findByFormatCode(String formatCode);
}
