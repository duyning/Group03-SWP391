/**
 * Interface Repository quản lý danh mục Phòng chiếu phim trong rạp (`rooms`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `RoomService`, `ShowtimeService`, `CatalogInitializer`.
 * - Hỗ trợ các chức năng: Lọc danh sách phòng theo điều kiện (`filterRooms`), kiểm tra trùng tên phòng trong rạp (`existsByRoomNameIgnoreCaseAndCinemaIdAndIdNot`),
 *   tìm kiếm phòng theo tên (`findFirstByRoomNameIgnoreCaseAndCinemaId`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 * Cập nhật bởi: TrienLX (25/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /** Tìm tất cả phòng theo ID rạp chiếu (`cinemaId`). */
    List<Room> findByCinemaId(Long cinemaId);

    /** Tìm bản ghi phòng chiếu đầu tiên khớp với tên phòng (không phân biệt hoa thường). */
    Optional<Room> findFirstByRoomNameIgnoreCase(String roomName);

    /** Kiểm tra tên phòng đã tồn tại trong rạp chiếu hay chưa. */
    boolean existsByRoomNameAndCinemaId(String roomName, Long cinemaId);

    /** Kiểm tra tên phòng đã tồn tại trong rạp chiếu hay chưa (không phân biệt chữ hoa/thường). */
    boolean existsByRoomNameIgnoreCaseAndCinemaId(String roomName, Long cinemaId);

    /** Kiểm tra tên phòng có trùng với phòng khác trong cùng rạp (bỏ qua ID hiện tại `id`) khi cập nhật. */
    boolean existsByRoomNameIgnoreCaseAndCinemaIdAndIdNot(String roomName, Long cinemaId, Long id);

    /** Tìm phòng chiếu đầu tiên theo tên phòng và ID rạp chiếu (phục vụ lấy RoomId khi map dữ liệu). */
    Optional<Room> findFirstByRoomNameIgnoreCaseAndCinemaId(String roomName, Long cinemaId);

    /** Đếm số phòng chiếu đang hoạt động theo trạng thái trong rạp. */
    long countByCinemaIdAndStatus(Long cinemaId, String status);

    /**
     * Lọc danh sách phòng chiếu đa tiêu chí dành cho quản lý phòng chiếu phía Admin.
     * Các tham số là tùy chọn - nếu null sẽ tự động bỏ qua điều kiện tương ứng.
     */
    @Query("""
        SELECT r FROM Room r
        WHERE r.cinemaId = :cinemaId
          AND (:roomName IS NULL OR r.roomName LIKE %:roomName%)
          AND (:roomType IS NULL OR r.roomType LIKE %:roomType%)
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
