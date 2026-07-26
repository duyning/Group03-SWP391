/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

/**
 * Entity đại diện cho một ô/vị trí ghế trong bảng `seats`.
 *
 * Vai trò trong luồng thiết kế ghế:
 * - `SeatController.saveMatrix(...)` nhận ma trận ghế từ giao diện.
 * - `SeatService.saveMatrix(...)` duyệt từng ô trong ma trận và tạo một entity `Seat`.
 * - `SeatRepository.saveAll(...)` lưu danh sách entity này xuống bảng `seats`.
 * - Khi khách đặt vé hoặc nhân viên bán vé tại quầy, hệ thống đọc lại bảng `seats`
 *   để dựng đúng sơ đồ ghế của phòng.
 *
 * Mô hình tọa độ:
 * - `roomId`: phòng sở hữu ghế.
 * - `rowIndex`: chỉ số hàng, bắt đầu từ 0. 0 tương ứng hàng A, 1 tương ứng hàng B.
 * - `colIndex`: chỉ số cột, bắt đầu từ 0. 0 tương ứng cột 1.
 * - Unique constraint `(room_id, row_index, col_index)` đảm bảo mỗi ô trong một phòng chỉ có một bản ghi.
 *
 * Mô hình loại ghế:
 * - `seatType` lưu mã loại ghế, ví dụ std, vip, couple hoặc mã loại ghế do admin tạo trong `seat_types`.
 * - `seatLabel` là nhãn hiển thị được sinh tự động, ví dụ A1, B12, C3-C4.
 * - `skip` là ô kỹ thuật nằm bên phải ghế couple; không bán vé riêng cho ô này.
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "row_index", "col_index"}))
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID Phòng chiếu sở hữu ghế này */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** Chỉ số hàng (0-based), 0 = hàng A, 1 = hàng B... */
    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    /** Chỉ số cột (0-based) */
    @Column(name = "col_index", nullable = false)
    private int colIndex;

    /**
     * Nhãn ghế hiển thị, ví dụ "A1", "B12", "C3-C4" (ghế đôi).
     * Được hệ thống tính tự động dựa trên vị trí tọa độ.
     */
    @Column(name = "seat_label", columnDefinition = "NVARCHAR(20)")
    private String seatLabel;

    /**
     * Loại ghế: std, vip, couple, broken, empty, skip.
     */
    @Column(name = "seat_type", nullable = false, columnDefinition = "NVARCHAR(30)")
    private String seatType = "std";

    public Seat() {
    }

    public Seat(Long id, Long roomId, int rowIndex, int colIndex, String seatLabel, String seatType) {
        this.id = id;
        this.roomId = roomId;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.seatLabel = seatLabel;
        this.seatType = seatType;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lớp Builder giúp xây dựng đối tượng Seat.
     */
    public static class Builder {
        private final Seat seat = new Seat();

        public Builder id(Long id) {
            seat.setId(id);
            return this;
        }

        public Builder roomId(Long roomId) {
            seat.setRoomId(roomId);
            return this;
        }

        public Builder rowIndex(int rowIndex) {
            seat.setRowIndex(rowIndex);
            return this;
        }

        public Builder colIndex(int colIndex) {
            seat.setColIndex(colIndex);
            return this;
        }

        public Builder seatLabel(String seatLabel) {
            seat.setSeatLabel(seatLabel);
            return this;
        }

        public Builder seatType(String seatType) {
            seat.setSeatType(seatType);
            return this;
        }

        public Seat build() {
            return seat;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    public String getSeatLabel() {
        return seatLabel;
    }

    public void setSeatLabel(String seatLabel) {
        this.seatLabel = seatLabel;
    }

    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }
}
