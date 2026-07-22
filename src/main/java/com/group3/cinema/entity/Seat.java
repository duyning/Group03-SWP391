/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

/**
 * Entity đại diện cho một ô ghế (`seats`) trong sơ đồ lưới của phòng chiếu.
 * 
 * Mỗi ghế gắn liền với một Room (`roomId`) và vị trí tọa độ (`rowIndex`, `colIndex`).
 * Phân loại ghế (`seatType`):
 * - "std"    : Ghế thường
 * - "vip"    : Ghế VIP
 * - "couple" : Ghế đôi (chiếm 2 cột liền kề)
 * - "broken" : Ghế hỏng / bảo trì
 * - "empty"  : Lối đi / Ô trống
 * - "skip"   : Ô bị chiếm bởi phần bên phải của ghế đôi (bỏ qua không render riêng)
 * 
 * Ngày cập nhật: 04/06/2026
 * Khởi tạo bởi: NinhDD - HE186113
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
