package example.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho một Phòng chiếu (Room) trong rạp chiếu phim.
 */
@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tên phòng chiếu, ví dụ: "Phòng 01", "Cinema 01" */
    @Column(name = "room_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String roomName;

    /** Loại phòng: 2D, 3D, IMAX, Gold */
    @Column(name = "room_type", columnDefinition = "NVARCHAR(20)")
    @Builder.Default
    private String roomType = "2D";

    /** Công nghệ âm thanh: "Dolby 7.1", "Dolby Atmos", "DTS:X" */
    @Column(name = "audio_tech", columnDefinition = "NVARCHAR(50)")
    @Builder.Default
    private String audioTech = "Dolby 7.1";

    /** Số hàng ghế (A, B, C...) */
    @Column(name = "rows", nullable = false)
    @Builder.Default
    private int rows = 8;

    /** Số ghế mỗi hàng */
    @Column(name = "cols", nullable = false)
    @Builder.Default
    private int cols = 15;

    /** Tổng sức chứa, được cập nhật sau khi lưu sơ đồ ghế */
    @Column(name = "total_seats")
    @Builder.Default
    private int totalSeats = 0;

    /** Trạng thái: "Hoạt động", "Bảo trì", "Tạm ngưng" */
    @Column(name = "status", columnDefinition = "NVARCHAR(20)")
    @Builder.Default
    private String status = "Hoạt động";

    /** ID rạp chiếu phim (foreign key, dùng đơn giản là Long) */
    @Column(name = "cinema_id")
    @Builder.Default
    private Long cinemaId = 1L;

}
