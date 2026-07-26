/**
 * Service quản lý các danh mục cấu hình dùng chung cho phòng và ghế.
 *
 * Service này nằm trước luồng tạo phòng và thiết kế ghế:
 * - Người quản lý tạo "Loại phòng" tại màn danh mục, ví dụ 2D, 3D, IMAX.
 * - Người quản lý tạo "Công nghệ âm thanh", ví dụ Dolby 7.1, Dolby Atmos.
 * - Người quản lý tạo "Loại ghế", ví dụ Ghế thường, Ghế VIP, Ghế đôi, Lối đi, Ghế hỏng.
 * - `RoomController` lấy loại phòng/âm thanh active để render dropdown khi tạo phòng.
 * - `RoomService` gọi lại repository danh mục để validate dữ liệu phòng trước khi lưu.
 * - `SeatController` lấy loại ghế active để render công cụ chọn loại ghế trong sơ đồ.
 * - `SeatService` dùng `SeatTypeRepository` để validate từng mã ghế và tính sức chứa.
 *
 * Ý nghĩa thiết kế:
 * - Project không fix cứng 2D/3D/Dolby/std/vip/couple trong form.
 * - Muốn chọn được loại phòng, âm thanh hoặc loại ghế thì admin phải tạo và bật active trước.
 * - Khi doanh nghiệp thêm định dạng mới, ví dụ Premium hoặc ScreenX, chỉ cần thêm danh mục, không sửa code.
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

    /** Lấy toàn bộ loại phòng, bao gồm cả active/inactive, để màn quản trị danh mục có thể quản lý đầy đủ. */
    public List<RoomType> getAllRoomTypes() {
        return roomTypeRepository.findAllByOrderByNameAsc();
    }

    /**
     * Lấy loại phòng đang hoạt động để đưa vào form tạo/sửa phòng.
     *
     * Luồng liên quan:
     * - `RoomController.listRooms(...)` gọi hàm này.
     * - View `manager_room.html` render dropdown/multi-select loại phòng.
     * - Người quản lý có thể chọn nhiều loại cùng lúc, ví dụ phòng hỗ trợ cả 2D và 3D.
     */
    public List<RoomType> getActiveRoomTypes() {
        return roomTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    /** Lấy toàn bộ công nghệ âm thanh để màn quản trị danh mục có thể xem cả active/inactive. */
    public List<AudioTechnology> getAllAudioTechnologies() {
        return audioTechnologyRepository.findAllByOrderByNameAsc();
    }

    /**
     * Lấy công nghệ âm thanh active để đưa vào dropdown tạo/sửa phòng.
     *
     * Dữ liệu từ hàm này là nguồn chọn chính thức của UI.
     * Sau đó `RoomService.validateAudioTech(...)` vẫn validate lại ở backend để tránh request giả.
     */
    public List<AudioTechnology> getActiveAudioTechnologies() {
        return audioTechnologyRepository.findByActiveTrueOrderByNameAsc();
    }

    /** Lấy toàn bộ loại ghế để admin quản lý danh mục loại ghế trong trang phòng/ghế. */
    public List<SeatType> getAllSeatTypes() {
        return seatTypeRepository.findAllByOrderByIdAsc();
    }

    /**
     * Lấy loại ghế đang active để admin dùng khi thiết kế sơ đồ.
     *
     * Luồng liên quan:
     * - `SeatController.seatDesignPage(...)` gọi hàm này.
     * - View nhận list loại ghế và hiển thị palette màu.
     * - Khi lưu, `SeatService.validateMatrix(...)` chỉ chấp nhận mã ghế active từ danh sách này.
     */
    public List<SeatType> getActiveSeatTypes() {
        return seatTypeRepository.findByActiveTrueOrderByIdAsc();
    }

    /**
     * Chuyển danh sách `SeatType` sang JSON để JavaScript vẽ palette loại ghế.
     *
     * Mỗi object JSON gồm:
     * - `code`: mã kỹ thuật lưu vào bảng `seats.seat_type`.
     * - `displayName`: tên hiển thị tiếng Việt.
     * - `color`: màu ô ghế trên sơ đồ.
     * - `capacity`: sức chứa dùng để tính tổng ghế của phòng.
     * - `sellable`: loại này có được bán vé hay chỉ là lối đi/hỏng.
     *
     * Hàm tự escape JSON vì project đang render trực tiếp sang Thymeleaf/JavaScript.
     */
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

    /**
     * Thêm mới một loại phòng chiếu.
     *
     * Sau khi thêm:
     * - Bản ghi mặc định active = true.
     * - `RoomController` sẽ lấy được loại phòng này trong dropdown tạo phòng.
     * - `RoomService.validateRoomTypes(...)` sẽ cho phép lưu phòng sử dụng loại này.
     */
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

    /**
     * Cập nhật loại phòng chiếu.
     *
     * Nếu `active = false`:
     * - Loại phòng vẫn còn trong DB để giữ lịch sử/dữ liệu phòng cũ.
     * - Nhưng form tạo/sửa phòng mới sẽ không còn hiển thị loại này.
     */
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

    /**
     * Thêm mới công nghệ âm thanh.
     *
     * Sau khi thêm và active:
     * - Dropdown âm thanh ở màn tạo/sửa phòng sẽ có thêm lựa chọn mới.
     * - Phòng chỉ lưu được âm thanh đã tồn tại trong danh mục, tránh nhập tay sai chính tả.
     */
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

    /**
     * Cập nhật công nghệ âm thanh.
     *
     * Nếu tắt active, công nghệ đó không còn được chọn cho phòng mới,
     * nhưng dữ liệu phòng cũ vẫn giữ nguyên để không làm mất thông tin lịch sử.
     */
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

    /**
     * Thêm mới một loại ghế xem phim.
     *
     * Luồng sau khi thêm:
     * - Service sinh `code` duy nhất từ tên ghế, ví dụ "Ghế Sofa" -> `ghe_sofa`.
     * - Bản ghi lưu vào `seat_types` với màu, sức chứa và cờ bán được/không bán được.
     * - `SeatController` đưa loại ghế này vào palette thiết kế sơ đồ.
     * - `SeatService.saveMatrix(...)` có thể lưu mã ghế này vào từng ô của bảng `seats`.
     *
     * Ý nghĩa `capacity` và `sellable`:
     * - Ghế bán được phải có capacity > 0.
     * - Lối đi, khoảng trống hoặc ghế hỏng thường `sellable = false`, `capacity = 0`.
     */
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

    /**
     * Cập nhật thuộc tính loại ghế.
     *
     * Project không cho sửa `displayName/code` ở hàm này để tránh làm lệch dữ liệu ghế đã lưu.
     * Admin chỉ chỉnh màu, sức chứa, trạng thái bán được và trạng thái active.
     *
     * Nếu `active = false`:
     * - Loại ghế cũ vẫn tồn tại trên các sơ đồ đã lưu.
     * - Nhưng khi thiết kế sơ đồ mới, loại ghế đó không còn trong palette active.
     */
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

    /**
     * Đồng bộ danh mục từ dữ liệu phòng sẵn có nhưng không tự thêm catalog mẫu.
     *
     * Hàm này giữ an toàn cho database thật:
     * - Chỉ đọc các phòng hiện có để đảm bảo room type/audio technology tương ứng có trong danh mục.
     * - Không tự insert các loại mẫu nếu không được bật bằng cấu hình riêng.
     */
    @Transactional
    public void seedFromExistingRooms() {
        seedFromExistingRooms(false);
    }

    /**
     * Đồng bộ danh mục từ phòng hiện có.
     *
     * `includeDefaultCatalogs = false`:
     * - Chỉ đảm bảo room type/audio technology đang tồn tại trong các phòng cũ có bản ghi danh mục tương ứng.
     *
     * `includeDefaultCatalogs = true`:
     * - Thêm dữ liệu mẫu 2D, Dolby 7.1, std, vip, couple, broken, empty nếu chưa tồn tại.
     * - Chỉ nên bật khi cần seed dữ liệu demo, không nên bật mặc định với database thật.
     */
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

