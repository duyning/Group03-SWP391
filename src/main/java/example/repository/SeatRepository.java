package example.repository;

import example.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** Lấy tất cả ghế của một phòng, sắp xếp theo hàng rồi cột */
    List<Seat> findByRoomIdOrderByRowIndexAscColIndexAsc(Long roomId);

    /** Xóa toàn bộ ghế của một phòng (để lưu sơ đồ mới) */
    @Modifying
    @Transactional
    @Query("DELETE FROM Seat s WHERE s.roomId = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);

    /** Đếm số ghế theo loại cho một phòng */
    long countByRoomIdAndSeatType(Long roomId, String seatType);

    /** Kiểm tra phòng đã có sơ đồ ghế chưa */
    boolean existsByRoomId(Long roomId);
}
