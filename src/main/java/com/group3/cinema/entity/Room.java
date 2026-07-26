/**
 * Entity đại diện cho phòng chiếu trong bảng `rooms`.
 *
 * Vai trò trong luồng tạo phòng và ghế:
 * - `RoomController` nhận request tạo/sửa/xóa phòng từ giao diện.
 * - `RoomService` validate dữ liệu rồi tạo/cập nhật entity này.
 * - `RoomRepository` lưu entity xuống bảng `rooms`.
 * - `SeatController` dùng `room.id` để mở màn thiết kế ghế `/admin/rooms/{roomId}/seats`.
 * - `SeatService.saveMatrix(...)` cập nhật lại `rows`, `cols`, `totalSeats` sau khi lưu sơ đồ ghế.
 *
 * Ý nghĩa dữ liệu:
 * - `roomName`: tên phòng dùng để hiển thị và liên kết với lịch chiếu.
 * - `roomType`: danh sách định dạng phòng hỗ trợ, ví dụ "2D, 3D"; dữ liệu lấy từ danh mục `room_types`.
 * - `audioTech`: công nghệ âm thanh; dữ liệu lấy từ danh mục `audio_technologies`.
 * - `rows`, `cols`: kích thước lưới vật lý của sơ đồ ghế.
 * - `totalSeats`: sức chứa bán vé thật, được tính từ bảng `seats` và `seat_types.capacity`,
 *   không nhập tay ở form tạo phòng.
 * - `status`: trạng thái vận hành. Phòng chỉ nên "Hoạt động" khi đã có sơ đồ ghế hợp lệ.
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tên phòng chiếu, ví dụ: "Phòng 01", "Cinema 01" */
    @Column(name = "room_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String roomName;

    /** Các định dạng chiếu phòng hỗ trợ, ví dụ: 2D, 3D, IMAX */
    @Column(name = "room_type", columnDefinition = "NVARCHAR(255)")
    private String roomType = "2D";

    /** Công nghệ âm thanh: "Dolby 7.1", "Dolby Atmos", "DTS:X" */
    @Column(name = "audio_tech", columnDefinition = "NVARCHAR(50)")
    private String audioTech = "Dolby 7.1";

    /** Số hàng ghế (A, B, C...) */
    @Column(name = "rows", nullable = false)
    private int rows = 8;

    /** Số ghế mỗi hàng */
    @Column(name = "cols", nullable = false)
    private int cols = 15;

    /** Tổng sức chứa, được cập nhật sau khi lưu sơ đồ ghế */
    @Column(name = "total_seats")
    private int totalSeats = 0;

    /** Trạng thái: "Hoạt động", "Bảo trì", "Tạm ngưng" */
    @Column(name = "status", columnDefinition = "NVARCHAR(20)")
    private String status = "Hoạt động";

    /** ID rạp chiếu phim (foreign key) */
    @Column(name = "cinema_id")
    private Long cinemaId = 1L;

    public Room() {
    }

    public Room(Long id, String roomName, String roomType, String audioTech,
                int rows, int cols, int totalSeats, String status, Long cinemaId) {
        this.id = id;
        this.roomName = roomName;
        this.roomType = roomType;
        this.audioTech = audioTech;
        this.rows = rows;
        this.cols = cols;
        this.totalSeats = totalSeats;
        this.status = status;
        this.cinemaId = cinemaId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lớp Builder thiết kế mẫu Pattern để xây dựng đối tượng Room một cách linh hoạt.
     */
    public static class Builder {
        private final Room room = new Room();

        public Builder id(Long id) {
            room.setId(id);
            return this;
        }

        public Builder roomName(String roomName) {
            room.setRoomName(roomName);
            return this;
        }

        public Builder roomType(String roomType) {
            room.setRoomType(roomType);
            return this;
        }

        public Builder audioTech(String audioTech) {
            room.setAudioTech(audioTech);
            return this;
        }

        public Builder rows(int rows) {
            room.setRows(rows);
            return this;
        }

        public Builder cols(int cols) {
            room.setCols(cols);
            return this;
        }

        public Builder totalSeats(int totalSeats) {
            room.setTotalSeats(totalSeats);
            return this;
        }

        public Builder status(String status) {
            room.setStatus(status);
            return this;
        }

        public Builder cinemaId(Long cinemaId) {
            room.setCinemaId(cinemaId);
            return this;
        }

        public Room build() {
            return room;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public String getAudioTech() {
        return audioTech;
    }

    public void setAudioTech(String audioTech) {
        this.audioTech = audioTech;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCinemaId() {
        return cinemaId;
    }

    public void setCinemaId(Long cinemaId) {
        this.cinemaId = cinemaId;
    }
}

