/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package example.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho một ô ghế trong sơ đồ phòng chiếu.
 * Mỗi ghế thuộc về một Room và được xác định bởi (rowIndex, colIndex).
 * type: "std" | "vip" | "couple" | "broken" | "empty" | "skip"
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "row_index", "col_index"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Phòng chiếu sở hữu ghế này */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** Chỉ số hàng (0-based), 0 = hàng A */
    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    /** Chỉ số cột (0-based) */
    @Column(name = "col_index", nullable = false)
    private int colIndex;

    /**
     * Nhãn ghế hiển thị, ví dụ "A1", "B12", "C3-C4" (couple).
     * Được tính tự động, không phải người dùng nhập.
     */
    @Column(name = "seat_label", length = 20)
    private String seatLabel;

    /**
     * Loại ghế:
     * - "std"    : Ghế thường
     * - "vip"    : Ghế VIP
     * - "couple" : Ghế đôi (chiếm 2 cột liền kề, cột kế tiếp sẽ là "skip")
     * - "broken" : Ghế hỏng / bảo trì
     * - "empty"  : Lối đi / ô trống
     * - "skip"   : Ô bị chiếm bởi couple bên trái (không render)
     */
    @Column(name = "seat_type", nullable = false, length = 10)
    @Builder.Default
    private String seatType = "std";
}
