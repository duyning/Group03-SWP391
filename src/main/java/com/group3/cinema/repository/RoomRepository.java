/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /** TÃ¬m táº¥t cáº£ phÃ²ng theo cinemaId */
    List<Room> findByCinemaId(Long cinemaId);

    /** Kiá»ƒm tra tÃªn phÃ²ng Ä‘Ã£ tá»“n táº¡i trong ráº¡p chÆ°a */
    boolean existsByRoomNameAndCinemaId(String roomName, Long cinemaId);

    boolean existsByRoomNameIgnoreCaseAndCinemaId(String roomName, Long cinemaId);

    boolean existsByRoomNameIgnoreCaseAndCinemaIdAndIdNot(String roomName, Long cinemaId, Long id);

    /** Äáº¿m sá»‘ phÃ²ng Ä‘ang hoáº¡t Ä‘á»™ng */
    long countByCinemaIdAndStatus(Long cinemaId, String status);

    /**
     * Lá»c phÃ²ng theo nhiá»u tiÃªu chÃ­ (JPQL).
     * CÃ¡c tham sá»‘ lÃ  optional â€“ náº¿u null thÃ¬ bá» qua Ä‘iá»u kiá»‡n Ä‘Ã³.
     */
    @Query("""
        SELECT r FROM Room r
        WHERE r.cinemaId = :cinemaId
          AND (:roomName IS NULL OR r.roomName LIKE %:roomName%)
          AND (:roomType IS NULL OR r.roomType = :roomType)
          AND (:status   IS NULL OR r.status   = :status)
          AND (:minSeats IS NULL OR r.totalSeats >= :minSeats)
        ORDER BY r.id ASC
    """)
    List<Room> filterRooms(
            @Param("cinemaId")  Long    cinemaId,
            @Param("roomName")  String  roomName,
            @Param("roomType")  String  roomType,
            @Param("status")    String  status,
            @Param("minSeats")  Integer minSeats
    );
}
