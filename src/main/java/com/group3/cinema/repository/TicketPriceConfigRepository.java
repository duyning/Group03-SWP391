/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: TicketPriceConfigRepository.java
 * Người tạo: TrienLX
 * Ngày tạo: 2026-06-25
 * Chi tiết: Khai báo repository cho TicketPriceConfig.
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.TicketPriceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.Optional;

@Repository
public interface TicketPriceConfigRepository extends JpaRepository<TicketPriceConfig, Long> {
    Optional<TicketPriceConfig> findByDayTypeAndSlotName(String dayType, String slotName);
}
