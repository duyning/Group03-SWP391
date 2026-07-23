/**
 * Service quản lý Phòng chiếu phim, Cấu hình công nghệ phòng (IMAX, 2D/3D), Âm thanh (Dolby Atmos) và Trạng thái vận hành phòng (`RoomService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `AdminRoomController` và `ShowtimeService`.
 * - Tương tác với:
 *   + `RoomRepository`: Lưu phòng (`save`), lọc phòng chiếu (`findByCinemaId`).
 *   + `RoomTypeRepository`: Kiểm tra loại phòng hợp lệ (`findByNameIgnoreCase`).
 *   + `AudioTechnologyRepository`: Kiểm tra công nghệ âm thanh active.
 *   + `SeatRepository`: Xóa sơ đồ ghế cũ (`deleteAllByRoomId`), kiểm tra tồn tại ghế (`existsByRoomId`).
 *   + `ShowtimeRepository`: Kiểm tra phòng đang vướng lịch chiếu (`existsByRoomIgnoreCaseAndShowDateGreaterThanEqual`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Room;
import com.group3.cinema.repository.AudioTechnologyRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.RoomTypeRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RoomService {

    private static final int ROOM_NAME_MAX_LENGTH = 100;
    private static final int ROOM_TYPE_MAX_LENGTH = 255;
    private static final int AUDIO_TECH_MAX_LENGTH = 50;
    private static final Set<String> ALLOWED_STATUSES = Set.of("Hoạt động", "Bảo trì", "Tạm ngưng");

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final AudioTechnologyRepository audioTechnologyRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;

    public RoomService(RoomRepository roomRepository,
                       RoomTypeRepository roomTypeRepository,
                       AudioTechnologyRepository audioTechnologyRepository,
                       SeatRepository seatRepository,
                       ShowtimeRepository showtimeRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.audioTechnologyRepository = audioTechnologyRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
    }

    /** Lấy toàn bộ danh sách phòng chiếu thuộc 1 rạp phim. */
    public List<Room> getAllRooms(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId);
    }

    /**
     * Lọc danh sách phòng chiếu theo tên, định dạng chiếu, âm thanh, trạng thái và tổng số ghế tối thiểu.
     */
    public List<Room> filterRooms(Long cinemaId, String roomName, String roomType,
                                   String audioTech, String status, Integer minSeats) {
        validateCinemaId(cinemaId);
        if (minSeats != null && minSeats < 0) {
            throw new IllegalArgumentException("Số ghế tối thiểu không được nhỏ hơn 0.");
        }
        String roomNameFilter = StringUtils.hasText(roomName) ? roomName.trim().toLowerCase() : null;
        String roomTypeFilter = StringUtils.hasText(roomType) ? roomType.trim().toLowerCase() : null;
        String audioTechFilter = StringUtils.hasText(audioTech) ? audioTech.trim().toLowerCase() : null;
        String statusFilter = normalizeStatus(status);

        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> roomNameFilter == null
                        || room.getRoomName().toLowerCase().contains(roomNameFilter))
                .filter(room -> roomTypeFilter == null
                        || (room.getRoomType() != null && room.getRoomType().toLowerCase().contains(roomTypeFilter)))
                .filter(room -> audioTechFilter == null
                        || (room.getAudioTech() != null && room.getAudioTech().toLowerCase().contains(audioTechFilter)))
                .filter(room -> statusFilter == null
                        || statusFilter.equals(room.getStatus()))
                .filter(room -> minSeats == null
                        || room.getTotalSeats() >= minSeats)
                .toList();
    }

    /** Lấy chi tiết 1 phòng chiếu theo ID. */
    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id)
                .map(room -> {
                    normalizeRoomStatus(room);
                    return room;
                });
    }

    /** Đếm tổng số phòng chiếu của rạp. */
    public long countTotal(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).size();
    }

    /** Đếm số phòng đang trong trạng thái "Hoạt động". */
    public long countActive(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> "Hoạt động".equals(room.getStatus()))
                .count();
    }

    /** Đếm số phòng đang trong trạng thái "Bảo trì". */
    public long countMaintenance(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> "Bảo trì".equals(room.getStatus()))
                .count();
    }

    /** Tính tổng sức chứa tất cả số ghế của toàn bộ các phòng trong rạp. */
    public long sumTotalSeats(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .mapToLong(Room::getTotalSeats)
                .sum();
    }

    /** Lấy danh sách các tùy chọn loại phòng hiện có của rạp. */
    public List<String> getRoomTypeOptions(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .map(Room::getRoomType)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** Lấy danh sách các tùy chọn công nghệ âm thanh của rạp. */
    public List<String> getAudioTechOptions(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .map(Room::getAudioTech)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** Lưu bản ghi phòng chiếu. */
    @Transactional
    public Room save(Room room) {
        return roomRepository.save(room);
    }

    /**
     * Khởi tạo phòng chiếu mới trong rạp (chặn đặt trạng thái Hoạt động khi chưa kẻ sơ đồ ghế).
     */
    @Transactional
    public Room addRoom(Long cinemaId, String roomName, List<String> roomTypes,
                        String audioTech, String status) {
        String cleanRoomName = validateRoomName(roomName);
        String cleanRoomType = validateRoomTypes(roomTypes);
        String cleanAudioTech = validateAudioTech(audioTech);
        String cleanStatus = validateStatus(status);
        validateCinemaId(cinemaId);
        validateDuplicateRoomName(cinemaId, cleanRoomName, null);
        if ("Hoạt động".equals(cleanStatus)) {
            throw new IllegalArgumentException("Phòng mới chưa có sơ đồ ghế nên chưa thể đặt trạng thái Hoạt động.");
        }

        Room room = Room.builder()
                .cinemaId(cinemaId)
                .roomName(cleanRoomName)
                .roomType(cleanRoomType)
                .audioTech(cleanAudioTech)
                .totalSeats(0)
                .status(cleanStatus)
                .build();
        return roomRepository.save(room);
    }

    /** Cập nhật thông tin phòng chiếu (kiểm tra không cho phép đổi tên nếu vướng lịch chiếu hiện tại/tương lai). */
    @Transactional
    public Room updateRoom(Long id, String roomName, List<String> roomTypes,
                           String audioTech, String status) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID phòng không hợp lệ.");
        }
        String cleanRoomName = validateRoomName(roomName);
        String cleanRoomType = validateRoomTypes(roomTypes);
        String cleanAudioTech = validateAudioTech(audioTech);
        String cleanStatus = validateStatus(status);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng id=" + id));
        validateCinemaId(room.getCinemaId());
        validateDuplicateRoomName(room.getCinemaId(), cleanRoomName, id);
        validateRoomCanBeActive(room, cleanStatus);
        validateRoomNameChange(room, cleanRoomName);
        room.setRoomName(cleanRoomName);
        room.setRoomType(cleanRoomType);
        room.setAudioTech(cleanAudioTech);
        room.setStatus(cleanStatus);
        return roomRepository.save(room);
    }

    /** Xóa phòng chiếu và xóa toàn bộ danh sách ghế thuộc phòng chiếu đó. */
    @Transactional
    public void deleteRoom(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID phòng không hợp lệ.");
        }
        if (!roomRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy phòng id=" + id);
        }
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng id=" + id));
        if (hasUpcomingShowtimes(room.getRoomName())) {
            throw new IllegalArgumentException("Phòng đang có lịch chiếu hiện tại hoặc tương lai. Vui lòng chuyển trạng thái sang Tạm ngưng/Bảo trì thay vì xóa.");
        }
        seatRepository.deleteAllByRoomId(id);
        roomRepository.deleteById(id);
    }

    private void normalizeRoomStatus(Room room) {
        room.setStatus(normalizeStatus(room.getStatus()));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String trimmed = status.trim();
        return switch (trimmed) {
            case "Ho?t d?ng", "Ho?t ??ng", "Hoat dong", "Hoáº¡t Ä‘á»™ng", "Hoạt động" -> "Hoạt động";
            case "B?o tr?", "Bao tri", "Báº£o trÃ¬", "Bảo trì" -> "Bảo trì";
            case "T?m ng?ng", "Tam ngung", "Táº¡m ngÆ°ng", "Tạm ngưng" -> "Tạm ngưng";
            default -> trimmed;
        };
    }

    private void validateCinemaId(Long cinemaId) {
        if (cinemaId == null || cinemaId <= 0) {
            throw new IllegalArgumentException("ID rạp không hợp lệ.");
        }
    }

    private String validateRoomName(String roomName) {
        if (!StringUtils.hasText(roomName)) {
            throw new IllegalArgumentException("Tên phòng không được để trống.");
        }
        String cleanRoomName = roomName.trim().replaceAll("\\s+", " ");
        if (cleanRoomName.length() > ROOM_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Tên phòng không được vượt quá " + ROOM_NAME_MAX_LENGTH + " ký tự.");
        }
        if (!cleanRoomName.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s._/-]*$")) {
            throw new IllegalArgumentException("Tên phòng chỉ được chứa chữ, số, khoảng trắng và các ký tự . _ / -.");
        }
        return cleanRoomName;
    }

    private String validateRoomTypes(List<String> roomTypes) {
        if (roomTypes == null || roomTypes.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một loại phòng.");
        }

        LinkedHashSet<String> cleanRoomTypes = new LinkedHashSet<>();
        for (String roomType : roomTypes) {
            if (!StringUtils.hasText(roomType)) {
                continue;
            }
            String cleanRoomType = roomType.trim();
            boolean active = roomTypeRepository.findByNameIgnoreCase(cleanRoomType)
                    .filter(type -> type.isActive())
                    .isPresent();
            if (!active) {
                throw new IllegalArgumentException("Loại phòng \"" + cleanRoomType + "\" không tồn tại hoặc đang bị tắt.");
            }
            cleanRoomTypes.add(cleanRoomType);
        }

        if (cleanRoomTypes.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một loại phòng.");
        }

        String joinedRoomTypes = String.join(", ", cleanRoomTypes);
        if (joinedRoomTypes.length() > ROOM_TYPE_MAX_LENGTH) {
            throw new IllegalArgumentException("Danh sách loại phòng không được vượt quá " + ROOM_TYPE_MAX_LENGTH + " ký tự.");
        }
        return joinedRoomTypes;
    }

    private String validateAudioTech(String audioTech) {
        if (!StringUtils.hasText(audioTech)) {
            throw new IllegalArgumentException("Công nghệ âm thanh không được để trống.");
        }
        String cleanAudioTech = audioTech.trim();
        if (cleanAudioTech.length() > AUDIO_TECH_MAX_LENGTH) {
            throw new IllegalArgumentException("Công nghệ âm thanh không được vượt quá " + AUDIO_TECH_MAX_LENGTH + " ký tự.");
        }
        boolean active = audioTechnologyRepository.findByNameIgnoreCase(cleanAudioTech)
                .filter(audio -> audio.isActive())
                .isPresent();
        if (!active) {
            throw new IllegalArgumentException("Công nghệ âm thanh không tồn tại hoặc đang bị tắt.");
        }
        return cleanAudioTech;
    }

    private String validateStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        if (!StringUtils.hasText(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái phòng không được để trống.");
        }
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái phòng không hợp lệ.");
        }
        return normalizedStatus;
    }

    private void validateDuplicateRoomName(Long cinemaId, String roomName, Long currentRoomId) {
        boolean duplicated = currentRoomId == null
                ? roomRepository.existsByRoomNameIgnoreCaseAndCinemaId(roomName, cinemaId)
                : roomRepository.existsByRoomNameIgnoreCaseAndCinemaIdAndIdNot(roomName, cinemaId, currentRoomId);
        if (duplicated) {
            throw new IllegalArgumentException("Tên phòng đã tồn tại trong rạp. Vui lòng dùng tên khác.");
        }
    }

    private void validateRoomCanBeActive(Room room, String status) {
        if (!"Hoạt động".equals(status)) {
            return;
        }
        if (room.getTotalSeats() <= 0 || !seatRepository.existsByRoomId(room.getId())) {
            throw new IllegalArgumentException("Phòng phải có sơ đồ ghế và sức chứa lớn hơn 0 trước khi bật Hoạt động.");
        }
    }

    private void validateRoomNameChange(Room room, String newRoomName) {
        if (room.getRoomName() == null || room.getRoomName().equalsIgnoreCase(newRoomName)) {
            return;
        }
        if (hasUpcomingShowtimes(room.getRoomName())) {
            throw new IllegalArgumentException("Không thể đổi tên phòng đang có lịch chiếu hiện tại hoặc tương lai vì lịch chiếu đang lưu theo tên phòng.");
        }
    }

    /** Kiểm tra xem phòng chiếu hiện có vướng lịch chiếu suất nào từ thời điểm hôm nay trở đi hay không. */
    public boolean hasUpcomingShowtimes(String roomName) {
        return StringUtils.hasText(roomName)
                && showtimeRepository.existsByRoomIgnoreCaseAndShowDateGreaterThanEqual(roomName.trim(), LocalDate.now());
    }
}

