package example.service;

import example.entity.Room;
import example.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    // ─── Lấy danh sách ──────────────────────────────────────────────────────

    /** Lấy tất cả phòng (không lọc) */
    public List<Room> getAllRooms(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId);
    }

    /**
     * Lọc phòng theo các tiêu chí tìm kiếm.
     * Chuỗi rỗng được coi là "không lọc" (null).
     */
    public List<Room> filterRooms(Long cinemaId, String roomName, String roomType,
                                   String audioTech, String status, Integer minSeats) {
        String roomNameFilter = StringUtils.hasText(roomName) ? roomName.trim().toLowerCase() : null;
        String roomTypeFilter = StringUtils.hasText(roomType) ? roomType.trim().toLowerCase() : null;
        String audioTechFilter = StringUtils.hasText(audioTech) ? audioTech.trim().toLowerCase() : null;
        String statusFilter = normalizeStatus(status);

        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> roomNameFilter == null
                        || room.getRoomName().toLowerCase().contains(roomNameFilter))
                .filter(room -> roomTypeFilter == null
                        || room.getRoomType().toLowerCase().contains(roomTypeFilter))
                .filter(room -> audioTechFilter == null
                        || room.getAudioTech().toLowerCase().contains(audioTechFilter))
                .filter(room -> statusFilter == null
                        || statusFilter.equals(room.getStatus()))
                .filter(room -> minSeats == null
                        || room.getTotalSeats() >= minSeats)
                .toList();
    }

    /** Tìm 1 phòng theo id */
    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id)
                .map(room -> {
                    normalizeRoomStatus(room);
                    return room;
                });
    }

    // ─── Thống kê ────────────────────────────────────────────────────────────

    public long countTotal(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).size();
    }

    public long countActive(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> "Hoạt động".equals(room.getStatus()))
                .count();
    }

    public long countMaintenance(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> "Bảo trì".equals(room.getStatus()))
                .count();
    }

    public long sumTotalSeats(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .mapToLong(Room::getTotalSeats)
                .sum();
    }

    public List<String> getRoomTypeOptions(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .map(Room::getRoomType)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public List<String> getAudioTechOptions(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .map(Room::getAudioTech)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    // ─── CRUD ────────────────────────────────────────────────────────────────

    @Transactional
    public Room save(Room room) {
        return roomRepository.save(room);
    }

    @Transactional
    public Room addRoom(Long cinemaId, String roomName, String roomType,
                        String audioTech, String status) {
        Room room = Room.builder()
                .cinemaId(cinemaId)
                .roomName(roomName.trim())
                .roomType(roomType.trim())
                .audioTech(audioTech.trim())
                .totalSeats(0)
                .status(normalizeStatus(status))
                .build();
        return roomRepository.save(room);
    }

    @Transactional
    public Room updateRoom(Long id, String roomName, String roomType,
                           String audioTech, String status) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng id=" + id));
        room.setRoomName(roomName.trim());
        room.setRoomType(roomType.trim());
        room.setAudioTech(audioTech.trim());
        room.setStatus(normalizeStatus(status));
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
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
            case "Ho?t d?ng", "Ho?t ??ng", "Hoat dong", "Hoạt động" -> "Hoạt động";
            case "B?o tr?", "Bao tri", "Bảo trì" -> "Bảo trì";
            case "T?m ng?ng", "Tam ngung", "Tạm ngưng" -> "Tạm ngưng";
            default -> trimmed;
        };
    }
}
