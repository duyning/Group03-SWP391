package com.group3.cinema.repository;

import com.group3.cinema.entity.TicketPriceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TicketPriceConfigRepository extends JpaRepository<TicketPriceConfig, Long> {
    Optional<TicketPriceConfig> findByDayTypeAndSlotName(String dayType, String slotName);
}
