/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package example.repository;

import example.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /** Tìm tất cả phòng theo cinemaId */
    List<Room> findByCinemaId(Long cinemaId);

    /** Kiểm tra tên phòng đã tồn tại trong rạp chưa */
    boolean existsByRoomNameAndCinemaId(String roomName, Long cinemaId);

    /** Đếm số phòng đang hoạt động */
    long countByCinemaIdAndStatus(Long cinemaId, String status);

    /**
     * Lọc phòng theo nhiều tiêu chí (JPQL).
     * Các tham số là optional – nếu null thì bỏ qua điều kiện đó.
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
