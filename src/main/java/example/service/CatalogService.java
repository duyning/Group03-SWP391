/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package example.service;

import example.entity.AudioTechnology;
import example.entity.Room;
import example.entity.RoomType;
import example.entity.SeatType;
import example.repository.AudioTechnologyRepository;
import example.repository.RoomRepository;
import example.repository.RoomTypeRepository;
import example.repository.SeatTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final RoomTypeRepository roomTypeRepository;
    private final AudioTechnologyRepository audioTechnologyRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final RoomRepository roomRepository;

    public List<RoomType> getAllRoomTypes() {
        return roomTypeRepository.findAllByOrderByNameAsc();
    }

    public List<RoomType> getActiveRoomTypes() {
        return roomTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<AudioTechnology> getAllAudioTechnologies() {
        return audioTechnologyRepository.findAllByOrderByNameAsc();
    }

    public List<AudioTechnology> getActiveAudioTechnologies() {
        return audioTechnologyRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<SeatType> getAllSeatTypes() {
        return seatTypeRepository.findAllByOrderByIdAsc();
    }

    public List<SeatType> getActiveSeatTypes() {
        return seatTypeRepository.findByActiveTrueOrderByIdAsc();
    }

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

    @Transactional
    public void addRoomType(String name, String description) {
        String cleanName = requireName(name, "Tên loại phòng không được để trống.");
        if (roomTypeRepository.existsByNameIgnoreCase(cleanName)) {
            throw new RuntimeException("Loại phòng đã tồn tại: " + cleanName);
        }
        roomTypeRepository.save(RoomType.builder()
                .name(cleanName)
                .description(cleanDescription(description))
                .active(true)
                .build());
    }

    @Transactional
    public void updateRoomType(Long id, String name, String description, boolean active) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phòng id=" + id));
        String cleanName = requireName(name, "Tên loại phòng không được để trống.");
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

    @Transactional
    public void addAudioTechnology(String name, String description) {
        String cleanName = requireName(name, "Tên công nghệ âm thanh không được để trống.");
        if (audioTechnologyRepository.existsByNameIgnoreCase(cleanName)) {
            throw new RuntimeException("Công nghệ âm thanh đã tồn tại: " + cleanName);
        }
        audioTechnologyRepository.save(AudioTechnology.builder()
                .name(cleanName)
                .description(cleanDescription(description))
                .active(true)
                .build());
    }

    @Transactional
    public void updateAudioTechnology(Long id, String name, String description, boolean active) {
        AudioTechnology audioTechnology = audioTechnologyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công nghệ âm thanh id=" + id));
        String cleanName = requireName(name, "Tên công nghệ âm thanh không được để trống.");
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

    @Transactional
    public void addSeatType(String displayName, String color, int capacity, boolean sellable) {
        String cleanName = requireName(displayName, "Tên loại ghế không được để trống.");
        String code = uniqueSeatTypeCode(cleanName);
        seatTypeRepository.save(SeatType.builder()
                .code(code)
                .displayName(cleanName)
                .color(requireColor(color))
                .capacity(Math.max(0, capacity))
                .sellable(sellable)
                .active(true)
                .build());
    }

    @Transactional
    public void updateSeatType(Long id, String color, int capacity, boolean sellable, boolean active) {
        SeatType seatType = seatTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại ghế id=" + id));
        seatType.setColor(requireColor(color));
        seatType.setCapacity(Math.max(0, capacity));
        seatType.setSellable(sellable);
        seatType.setActive(active);
        seatTypeRepository.save(seatType);
    }

    @Transactional
    public void seedFromExistingRooms() {
        for (Room room : roomRepository.findAll()) {
            ensureRoomType(room.getRoomType());
            ensureAudioTechnology(room.getAudioTech());
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
        return name.trim();
    }

    private String cleanDescription(String description) {
        return StringUtils.hasText(description) ? description.trim() : "";
    }

    private String requireColor(String color) {
        if (!StringUtils.hasText(color) || !color.trim().matches("^#[0-9a-fA-F]{6}$")) {
            throw new RuntimeException("Màu loại ghế phải có định dạng #RRGGBB.");
        }
        return color.trim();
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
