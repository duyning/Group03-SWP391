package example.service;

import example.entity.AudioTechnology;
import example.entity.Room;
import example.entity.RoomType;
import example.repository.AudioTechnologyRepository;
import example.repository.RoomRepository;
import example.repository.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final RoomTypeRepository roomTypeRepository;
    private final AudioTechnologyRepository audioTechnologyRepository;
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
    public void seedFromExistingRooms() {
        for (Room room : roomRepository.findAll()) {
            ensureRoomType(room.getRoomType());
            ensureAudioTechnology(room.getAudioTech());
        }
        ensureRoomType("2D");
        ensureAudioTechnology("Dolby 7.1");
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

    private String requireName(String name, String message) {
        if (!StringUtils.hasText(name)) {
            throw new RuntimeException(message);
        }
        return name.trim();
    }

    private String cleanDescription(String description) {
        return StringUtils.hasText(description) ? description.trim() : "";
    }
}
