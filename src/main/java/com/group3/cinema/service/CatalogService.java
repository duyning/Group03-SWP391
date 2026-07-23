/**
 * Service quản lý danh mục cấu hình hệ thống bao gồm: Loại phòng chiếu (`RoomType`), Công nghệ âm thanh (`AudioTechnology`), và Loại ghế (`SeatType`) (`CatalogService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `CatalogController`, `RoomService`, `CatalogInitializer`.
 * - Tương tác với các Repository:
 *   + `RoomTypeRepository`: Thao tác dữ liệu loại phòng (`2D`, `3D`, `IMAX`).
 *   + `AudioTechnologyRepository`: Thao tác công nghệ âm thanh (`Dolby 7.1`, `Dolby Atmos`).
 *   + `SeatTypeRepository`: Thao tác loại ghế (`Ghế thường`, `VIP`, `Couple`).
 *   + `RoomRepository`: Duyệt phòng hiện tại để tự động khởi tạo danh mục ban đầu (`seedFromExistingRooms`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.AudioTechnology;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.RoomType;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.repository.AudioTechnologyRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.RoomTypeRepository;
import com.group3.cinema.repository.SeatTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class CatalogService {

    private static final int ROOM_TYPE_NAME_MAX_LENGTH = 50;
    private static final int AUDIO_NAME_MAX_LENGTH = 80;
    private static final int SEAT_TYPE_NAME_MAX_LENGTH = 80;
    private static final int DESCRIPTION_MAX_LENGTH = 255;
    private static final int MIN_SEAT_CAPACITY = 0;
    private static final int MAX_SEAT_CAPACITY = 10;

    private final RoomTypeRepository roomTypeRepository;
    private final AudioTechnologyRepository audioTechnologyRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final RoomRepository roomRepository;

    public CatalogService(RoomTypeRepository roomTypeRepository,
                          AudioTechnologyRepository audioTechnologyRepository,
                          SeatTypeRepository seatTypeRepository,
                          RoomRepository roomRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.audioTechnologyRepository = audioTechnologyRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.roomRepository = roomRepository;
    }

    /** Lấy toàn bộ danh sách loại phòng chiếu. */
    public List<RoomType> getAllRoomTypes() {
        return roomTypeRepository.findAllByOrderByNameAsc();
    }

    /** Lấy danh sách loại phòng chiếu đang hoạt động (`active = true`). */
    public List<RoomType> getActiveRoomTypes() {
        return roomTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    /** Lấy tất cả danh sách công nghệ âm thanh. */
    public List<AudioTechnology> getAllAudioTechnologies() {
        return audioTechnologyRepository.findAllByOrderByNameAsc();
    }

    /** Lấy danh sách công nghệ âm thanh đang active. */
    public List<AudioTechnology> getActiveAudioTechnologies() {
        return audioTechnologyRepository.findByActiveTrueOrderByNameAsc();
    }

    /** Lấy tất cả danh sách loại ghế. */
    public List<SeatType> getAllSeatTypes() {
        return seatTypeRepository.findAllByOrderByIdAsc();
    }

    /** Lấy danh sách loại ghế đang active. */
    public List<SeatType> getActiveSeatTypes() {
        return seatTypeRepository.findByActiveTrueOrderByIdAsc();
    }

    /** Chuyển đổi danh sách loại ghế sang mảng định dạng chuỗi JSON cho giao diện sơ đồ ghế. */
    public String seatTypesToJson(List<SeatType> seatTypes) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < seatTypes.size(); i++) {
            SeatType type = seatTypes.get(i);
            json.append("{")
                    .append("\"code\":\"").append(escapeJson(type.getCode())).append("\",")
                    .append("\"displayName\":\"").append(escapeJson(type.getDisplayName())).append("\",")
                    .append("\"color\":\"").append(escapeJson(type.getColor())).append("\",")
                    .append("\"capacity\":").append(type.getCapacity()).append(",")
                    .append("\"sellable\":").append(type.isSellable())
                    .append("}");
            if (i < seatTypes.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    /** Thêm mới một loại phòng chiếu. */
    @Transactional
    public void addRoomType(String name, String description) {
        String cleanName = requireName(name, "Tên loại phòng không được để trống.");
        validateLength(cleanName, ROOM_TYPE_NAME_MAX_LENGTH, "Tên loại phòng");
        if (roomTypeRepository.existsByNameIgnoreCase(cleanName)) {
            throw new RuntimeException("Loại phòng đã tồn tại: " + cleanName);
        }
        roomTypeRepository.save(RoomType.builder()
                .name(cleanName)
                .description(cleanDescription(description))
                .active(true)
                .build());
    }

    /** Cập nhật thông tin loại phòng chiếu. */
    @Transactional
    public void updateRoomType(Long id, String name, String description, boolean active) {
        validateId(id, "ID loại phòng không hợp lệ.");
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phòng id=" + id));
        String cleanName = requireName(name, "Tên loại phòng không được để trống.");
        validateLength(cleanName, ROOM_TYPE_NAME_MAX_LENGTH, "Tên loại phòng");
        roomTypeRepository.findByNameIgnoreCase(cleanName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("Loại phòng đã tồn tại: " + cleanName);
                });
        roomType.setName(cleanName);
        roomType.setDescription(cleanDescription(description));
        roomType.setActive(active);
        roomTypeRepository.save(roomType);
    }

    /** Thêm mới một công nghệ âm thanh. */
    @Transactional
    public void addAudioTechnology(String name, String description) {
        String cleanName = requireName(name, "Tên công nghệ âm thanh không được để trống.");
        validateLength(cleanName, AUDIO_NAME_MAX_LENGTH, "Tên công nghệ âm thanh");
        if (audioTechnologyRepository.existsByNameIgnoreCase(cleanName)) {
            throw new RuntimeException("Công nghệ âm thanh đã tồn tại: " + cleanName);
        }
        audioTechnologyRepository.save(AudioTechnology.builder()
                .name(cleanName)
                .description(cleanDescription(description))
                .active(true)
                .build());
    }

    /** Cập nhật thông tin công nghệ âm thanh. */
    @Transactional
    public void updateAudioTechnology(Long id, String name, String description, boolean active) {
        validateId(id, "ID công nghệ âm thanh không hợp lệ.");
        AudioTechnology audioTechnology = audioTechnologyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công nghệ âm thanh id=" + id));
        String cleanName = requireName(name, "Tên công nghệ âm thanh không được để trống.");
        validateLength(cleanName, AUDIO_NAME_MAX_LENGTH, "Tên công nghệ âm thanh");
        audioTechnologyRepository.findByNameIgnoreCase(cleanName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("Công nghệ âm thanh đã tồn tại: " + cleanName);
                });
        audioTechnology.setName(cleanName);
        audioTechnology.setDescription(cleanDescription(description));
        audioTechnology.setActive(active);
        audioTechnologyRepository.save(audioTechnology);
    }

    /** Thêm mới một loại ghế xem phim. */
    @Transactional
    public void addSeatType(String displayName, String color, int capacity, boolean sellable) {
        String cleanName = requireName(displayName, "Tên loại ghế không được để trống.");
        validateLength(cleanName, SEAT_TYPE_NAME_MAX_LENGTH, "Tên loại ghế");
        validateSeatTypeNameUnique(cleanName, null);
        int cleanCapacity = validateCapacity(capacity, sellable);
        String code = uniqueSeatTypeCode(cleanName);
        seatTypeRepository.save(SeatType.builder()
                .code(code)
                .displayName(cleanName)
                .color(requireColor(color))
                .capacity(cleanCapacity)
                .sellable(sellable)
                .active(true)
                .build());
    }

    /** Cập nhật thuộc tính hiển thị, màu sắc, sức chứa của loại ghế. */
    @Transactional
    public void updateSeatType(Long id, String color, int capacity, boolean sellable, boolean active) {
        validateId(id, "ID loại ghế không hợp lệ.");
        SeatType seatType = seatTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại ghế id=" + id));
        seatType.setColor(requireColor(color));
        seatType.setCapacity(validateCapacity(capacity, sellable));
        seatType.setSellable(sellable);
        seatType.setActive(active);
        seatTypeRepository.save(seatType);
    }

    /** Tự động khởi tạo danh mục mẫu loại phòng, âm thanh và các loại ghế từ dữ liệu phòng sẵn có. */
    @Transactional
    public void seedFromExistingRooms() {
        seedFromExistingRooms(false);
    }

    /** Đồng bộ danh mục từ phòng hiện có; chỉ thêm dữ liệu mẫu khi được bật bằng cấu hình riêng. */
    @Transactional
    public void seedFromExistingRooms(boolean includeDefaultCatalogs) {
        for (Room room : roomRepository.findAll()) {
            ensureRoomType(room.getRoomType());
            ensureAudioTechnology(room.getAudioTech());
        }
        if (!includeDefaultCatalogs) {
            return;
        }
        ensureRoomType("2D");
        ensureAudioTechnology("Dolby 7.1");
        ensureSeatType("std", "Ghế thường", "#e2e8f0", 1, true);
        ensureSeatType("vip", "Ghế VIP", "#fef08a", 1, true);
        ensureSeatType("couple", "Ghế Couple", "#fbcfe8", 2, true);
        ensureSeatType("broken", "Ghế hỏng", "#fca5a5", 0, false);
        ensureSeatType("empty", "Lối đi / Trống", "#ffffff", 0, false);
    }

    private void ensureRoomType(String name) {
        if (StringUtils.hasText(name) && !roomTypeRepository.existsByNameIgnoreCase(name.trim())) {
            roomTypeRepository.save(RoomType.builder().name(name.trim()).active(true).build());
        }
    }

    private void ensureAudioTechnology(String name) {
        if (StringUtils.hasText(name) && !audioTechnologyRepository.existsByNameIgnoreCase(name.trim())) {
            audioTechnologyRepository.save(AudioTechnology.builder().name(name.trim()).active(true).build());
        }
    }

    private void ensureSeatType(String code, String displayName, String color, int capacity, boolean sellable) {
        if (!seatTypeRepository.existsByCodeIgnoreCase(code)) {
            seatTypeRepository.save(SeatType.builder()
                    .code(code)
                    .displayName(displayName)
                    .color(color)
                    .capacity(capacity)
                    .sellable(sellable)
                    .active(true)
                    .build());
        }
    }

    private String requireName(String name, String message) {
        if (!StringUtils.hasText(name)) {
            throw new RuntimeException(message);
        }
        String cleanName = name.trim().replaceAll("\\s+", " ");
        if (!cleanName.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s._/+:-]*$")) {
            throw new RuntimeException("Tên chỉ được chứa chữ, số, khoảng trắng và các ký tự . _ / + : -.");
        }
        return cleanName;
    }

    private String cleanDescription(String description) {
        String cleanDescription = StringUtils.hasText(description) ? description.trim() : "";
        validateLength(cleanDescription, DESCRIPTION_MAX_LENGTH, "Mô tả");
        return cleanDescription;
    }

    private String requireColor(String color) {
        if (!StringUtils.hasText(color) || !color.trim().matches("^#[0-9a-fA-F]{6}$")) {
            throw new RuntimeException("Màu loại ghế phải có định dạng #RRGGBB.");
        }
        return color.trim();
    }

    private void validateLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new RuntimeException(fieldName + " không được vượt quá " + maxLength + " ký tự.");
        }
    }

    private void validateId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new RuntimeException(message);
        }
    }

    private int validateCapacity(int capacity, boolean sellable) {
        if (capacity < MIN_SEAT_CAPACITY || capacity > MAX_SEAT_CAPACITY) {
            throw new RuntimeException("Sức chứa loại ghế phải từ " + MIN_SEAT_CAPACITY + " đến " + MAX_SEAT_CAPACITY + ".");
        }
        if (sellable && capacity <= 0) {
            throw new RuntimeException("Loại ghế có bán phải có sức chứa lớn hơn 0.");
        }
        return capacity;
    }

    private void validateSeatTypeNameUnique(String displayName, Long currentId) {
        boolean duplicated = seatTypeRepository.findAll().stream()
                .anyMatch(type -> type.getDisplayName() != null
                        && type.getDisplayName().equalsIgnoreCase(displayName)
                        && (currentId == null || !type.getId().equals(currentId)));
        if (duplicated) {
            throw new RuntimeException("Loại ghế đã tồn tại: " + displayName);
        }
    }

    private String uniqueSeatTypeCode(String displayName) {
        String base = Normalizer.normalize(displayName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(base)) {
            base = "seat";
        }

        String code = base;
        int index = 2;
        while (seatTypeRepository.existsByCodeIgnoreCase(code)) {
            code = base + "_" + index++;
        }
        return code;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

