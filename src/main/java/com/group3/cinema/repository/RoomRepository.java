/**
 * Repository thao tác bảng `rooms`.
 *
 * Nằm ở cuối luồng quản lý phòng:
 * - `RoomController` nhận request từ UI.
 * - `RoomService` validate nghiệp vụ.
 * - `RoomRepository` thực hiện truy vấn/lưu/xóa dữ liệu phòng trong DB.
 *
 * Các nhóm truy vấn chính:
 * - Lấy danh sách phòng theo rạp để render `/admin/rooms`.
 * - Kiểm tra trùng tên phòng trong cùng rạp khi tạo/sửa.
 * - Tìm phòng theo tên khi các module khác cần map lịch chiếu/phòng.
 * - Lọc phòng theo nhiều tiêu chí phục vụ màn quản lý.
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

    /** Tìm tất cả phòng theo ID rạp chiếu (`cinemaId`) để render bảng danh sách phòng. */
    List<Room> findByCinemaId(Long cinemaId);

    /** Tìm bản ghi phòng chiếu đầu tiên khớp với tên phòng (không phân biệt hoa thường). */
    Optional<Room> findFirstByRoomNameIgnoreCase(String roomName);

    /** Kiểm tra tên phòng đã tồn tại trong rạp chiếu hay chưa. */
    boolean existsByRoomNameAndCinemaId(String roomName, Long cinemaId);

    /** Kiểm tra tên phòng đã tồn tại trong rạp chiếu hay chưa, dùng khi tạo phòng mới. */
    boolean existsByRoomNameIgnoreCaseAndCinemaId(String roomName, Long cinemaId);

    /** Kiểm tra tên phòng có trùng với phòng khác trong cùng rạp, bỏ qua chính phòng đang sửa. */
    boolean existsByRoomNameIgnoreCaseAndCinemaIdAndIdNot(String roomName, Long cinemaId, Long id);

    /** Tìm phòng chiếu đầu tiên theo tên phòng và ID rạp chiếu (phục vụ lấy RoomId khi map dữ liệu). */
    Optional<Room> findFirstByRoomNameIgnoreCaseAndCinemaId(String roomName, Long cinemaId);

    /** Đếm số phòng chiếu đang hoạt động theo trạng thái trong rạp. */
    long countByCinemaIdAndStatus(Long cinemaId, String status);

    /**
     * Lọc danh sách phòng chiếu đa tiêu chí dành cho màn quản lý phòng.
     *
     * Tham số nào null thì bỏ qua điều kiện đó. Query này không xử lý audioTech,
     * vì `RoomService.filterRooms(...)` hiện lọc audioTech ở tầng service để đồng bộ normalize tiếng Việt/trạng thái.
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
