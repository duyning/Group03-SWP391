/**
 * Repository thao tác bảng `seats`, tức từng ô ghế/lối đi/hỏng trong sơ đồ phòng.
 *
 * Luồng gọi & sử dụng:
 * - `SeatController` nhận request mở/lưu sơ đồ ghế từ `manager_seat.html`.
 * - `SeatService` validate ma trận, sinh nhãn ghế và tính tổng sức chứa.
 * - `RoomService` và màn quản trị dùng để lưu/reset sơ đồ phòng.
 * - `BookingShowtimeService` đọc sơ đồ để tính tổng sức chứa còn bán.
 * - `SeatHoldingService` đọc/kiểm tra ID ghế, tọa độ, loại ghế và dựng `BookingSeatView`.
 * - `CatalogInitializer` dùng khi khởi tạo dữ liệu cấu hình ban đầu.
 *
 * Vai trò chính trong nghiệp vụ:
 * - Khi mở trang thiết kế, repository trả toàn bộ ghế theo row/col để dựng lại ma trận đúng vị trí.
 * - Khi lưu sơ đồ mới, service xóa toàn bộ ghế cũ của phòng rồi save lại ma trận mới.
 * - Khi tạo/sửa phòng, `RoomService` dùng `existsByRoomId(...)` để biết phòng đã có sơ đồ ghế hay chưa
 *   trước khi cho bật trạng thái "Hoạt động".
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

    /**
     * Lấy toàn bộ ghế của một phòng, sắp xếp từ trên xuống dưới và từ trái sang phải.
     *
     * Luồng sử dụng:
     * - `SeatService.buildMatrix(roomId)` gọi hàm này.
     * - Service tạo mảng `String[rows][cols]`.
     * - Từng bản ghi trả về sẽ được đặt vào đúng `matrix[rowIndex][colIndex]`.
     * - View nhận matrix JSON và vẽ sơ đồ giống dữ liệu đã lưu trong DB.
     */
    List<Seat> findByRoomIdOrderByRowIndexAscColIndexAsc(Long roomId);

    /**
     * Xóa toàn bộ sơ đồ ghế hiện tại của một phòng.
     *
     * Luồng sử dụng:
     * - `SeatService.saveMatrix(roomId, matrix)` gọi trước khi insert ma trận mới.
     * - `RoomService.deleteRoom(id)` gọi trước khi xóa phòng.
     *
     * Lý do thiết kế:
     * - Sơ đồ ghế là một ma trận hoàn chỉnh, không phải từng ghế chỉnh riêng lẻ.
     * - Khi admin giảm hàng/cột hoặc đổi nhiều loại ghế, xóa toàn bộ rồi tạo lại giúp DB không còn ghế dư.
     * - Việc cho phép xóa/lưu đã được service kiểm tra trước bằng ràng buộc lịch chiếu.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Seat s WHERE s.roomId = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);

    /**
     * Đếm số ô đang dùng một loại ghế cụ thể trong phòng.
     *
     * Chủ yếu phục vụ phần thống kê nhanh ở màn thiết kế ghế.
     * Lưu ý: đây là số ô theo loại, không phải luôn là sức chứa; ví dụ ghế couple có thể tính 2 chỗ.
     */
    long countByRoomIdAndSeatType(Long roomId, String seatType);

    /**
     * Kiểm tra phòng đã có ít nhất một bản ghi ghế hay chưa.
     *
     * `RoomService.validateRoomCanBeActive(...)` dùng kết quả này để chặn phòng chuyển sang "Hoạt động"
     * khi người quản lý mới tạo phòng nhưng chưa thiết kế sơ đồ ghế.
     */
    boolean existsByRoomId(Long roomId);
}

