/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** Láº¥y táº¥t cáº£ gháº¿ cá»§a má»™t phÃ²ng, sáº¯p xáº¿p theo hÃ ng rá»“i cá»™t */
    List<Seat> findByRoomIdOrderByRowIndexAscColIndexAsc(Long roomId);

    /** XÃ³a toÃ n bá»™ gháº¿ cá»§a má»™t phÃ²ng (Ä‘á»ƒ lÆ°u sÆ¡ Ä‘á»“ má»›i) */
    @Modifying
    @Transactional
    @Query("DELETE FROM Seat s WHERE s.roomId = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);

    /** Äáº¿m sá»‘ gháº¿ theo loáº¡i cho má»™t phÃ²ng */
    long countByRoomIdAndSeatType(Long roomId, String seatType);

    /** Kiá»ƒm tra phÃ²ng Ä‘Ã£ cÃ³ sÆ¡ Ä‘á»“ gháº¿ chÆ°a */
    boolean existsByRoomId(Long roomId);
}
