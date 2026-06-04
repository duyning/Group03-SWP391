package example.service;

import example.entity.Room;
import example.entity.Seat;
import example.entity.SeatType;
import example.repository.RoomRepository;
import example.repository.SeatRepository;
import example.repository.SeatTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final RoomRepository  roomRepository;
    private final SeatTypeRepository seatTypeRepository;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // ─── Đọc sơ đồ ghế ──────────────────────────────────────────────────────

    /**
     * Lấy toàn bộ ghế của phòng, đã sắp xếp theo (rowIndex, colIndex).
     * Nếu phòng chưa có sơ đồ → trả về danh sách rỗng (frontend sẽ dùng
     * initData() tạo mặc định).
     */
    public List<Seat> getSeatsForRoom(Long roomId) {
        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomId);
    }

    /**
     * Chuyển danh sách Seat thành mảng 2D String (chỉ chứa seatType),
     * kích thước [rows][cols] lấy từ Room entity.
     * Dùng để truyền sang JavaScript qua Thymeleaf (JSON).
     */
    public String[][] buildMatrix(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        int rows = room.getRows();
        int cols = room.getCols();
        String[][] matrix = new String[rows][cols];

        // Khởi tạo mặc định "std"
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                matrix[r][c] = "std";

        // Ghi đè theo dữ liệu đã lưu
        List<Seat> seats = seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomId);
        for (Seat seat : seats) {
            int r = seat.getRowIndex();
            int c = seat.getColIndex();
            if (r < rows && c < cols) {
                matrix[r][c] = seat.getSeatType();
            }
        }
        return matrix;
    }

    /**
     * Chuyển matrix 2D thành chuỗi JSON để embed vào script Thymeleaf.
     * Ví dụ: [["std","vip"],["couple","skip"]]
     */
    public String matrixToJson(String[][] matrix) {
        StringBuilder sb = new StringBuilder("[");
        for (int r = 0; r < matrix.length; r++) {
            sb.append("[");
            for (int c = 0; c < matrix[r].length; c++) {
                sb.append("\"").append(matrix[r][c]).append("\"");
                if (c < matrix[r].length - 1) sb.append(",");
            }
            sb.append("]");
            if (r < matrix.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ─── Lưu sơ đồ ghế ──────────────────────────────────────────────────────

    /**
     * Lưu toàn bộ sơ đồ ghế:
     * 1. Xóa tất cả ghế cũ của phòng
     * 2. Tạo mới theo matrixJson gửi từ trình duyệt
     *
     * @param roomId     ID phòng
     * @param matrixJson JSON 2D mảng type: [["std","vip",...], ...]
     */
    @Transactional
    public void saveMatrix(Long roomId, String[][] matrixJson) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
        List<String> errors = validateMatrix(matrixJson);
        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join(" ", errors));
        }

        // Xóa sơ đồ cũ
        seatRepository.deleteAllByRoomId(roomId);

        int rows = matrixJson.length;
        List<Seat> newSeats = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            int cols = matrixJson[r].length;
            for (int c = 0; c < cols; c++) {
                String seatType = matrixJson[r][c];
                String rowLetter = (r < ALPHABET.length()) ? String.valueOf(ALPHABET.charAt(r)) : "?";
                String label = buildLabel(seatType, rowLetter, c, cols, matrixJson[r]);

                Seat seat = Seat.builder()
                        .roomId(roomId)
                        .rowIndex(r)
                        .colIndex(c)
                        .seatType(seatType)
                        .seatLabel(label)
                        .build();
                newSeats.add(seat);
            }
        }

        seatRepository.saveAll(newSeats);

        Map<String, Integer> capacityByType = seatTypeRepository.findAll().stream()
                .collect(Collectors.toMap(SeatType::getCode, SeatType::getCapacity, (a, b) -> a));
        int totalCapacity = newSeats.stream()
                .filter(seat -> !"skip".equals(seat.getSeatType()))
                .mapToInt(seat -> capacityByType.getOrDefault(seat.getSeatType(), 0))
                .sum();
        room.setRows(rows);
        room.setCols(rows > 0 ? matrixJson[0].length : 0);
        room.setTotalSeats(totalCapacity);
        room.setStatus(normalizeStatus(room.getStatus()));
        roomRepository.save(room);
    }

    public List<String> validateMatrix(String[][] matrix) {
        List<String> errors = new ArrayList<>();
        if (matrix == null || matrix.length == 0) {
            errors.add("Sơ đồ ghế phải có ít nhất 1 hàng.");
            return errors;
        }
        if (matrix.length > 26) {
            errors.add("Sơ đồ ghế không được vượt quá 26 hàng.");
        }

        int cols = matrix[0] == null ? 0 : matrix[0].length;
        if (cols == 0) {
            errors.add("Sơ đồ ghế phải có ít nhất 1 cột.");
            return errors;
        }
        if (cols > 50) {
            errors.add("Sơ đồ ghế không được vượt quá 50 cột.");
        }

        Set<String> allowedTypes = seatTypeRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(SeatType::getCode)
                .collect(Collectors.toSet());
        allowedTypes.add("skip");
        Map<String, Integer> orphanSkipByRow = new HashMap<>();

        for (int r = 0; r < matrix.length; r++) {
            if (matrix[r] == null || matrix[r].length != cols) {
                errors.add("Các hàng trong sơ đồ ghế phải có cùng số cột.");
                break;
            }
            for (int c = 0; c < cols; c++) {
                String type = matrix[r][c];
                if (type == null || !allowedTypes.contains(type)) {
                    errors.add("Loại ghế không hợp lệ tại hàng " + rowName(r) + ", cột " + (c + 1) + ".");
                    continue;
                }
                if ("couple".equals(type)) {
                    if (c >= cols - 1) {
                        errors.add("Ghế couple không được nằm ở cột cuối hàng " + rowName(r) + ".");
                    } else if (!"skip".equals(matrix[r][c + 1])) {
                        errors.add("Ghế couple tại " + rowName(r) + (c + 1) + " phải chiếm ô bên phải.");
                    }
                }
                if ("skip".equals(type) && (c == 0 || !"couple".equals(matrix[r][c - 1]))) {
                    orphanSkipByRow.put(rowName(r), orphanSkipByRow.getOrDefault(rowName(r), 0) + 1);
                }
            }
        }

        orphanSkipByRow.forEach((row, count) ->
                errors.add("Hàng " + row + " có ô skip lẻ, không thuộc ghế couple."));
        return errors;
    }

    private String rowName(int rowIndex) {
        return rowIndex < ALPHABET.length() ? String.valueOf(ALPHABET.charAt(rowIndex)) : "?";
    }

    /** Sinh nhãn ghế dựa theo loại */
    private String buildLabel(String type, String rowLetter, int c,
                               int totalCols, String[] rowTypes) {
        return switch (type) {
            case "couple" -> rowLetter + (c + 1) + "-" + rowLetter + (c + 2);
            case "empty", "skip", "broken" -> "";
            default -> rowLetter + (c + 1); // std / vip
        };
    }

    // ─── Thống kê ─────────────────────────────────────────────────────────────

    public long countByType(Long roomId, String type) {
        return seatRepository.countByRoomIdAndSeatType(roomId, type);
    }

    public boolean hasSeats(Long roomId) {
        return seatRepository.existsByRoomId(roomId);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return status;
        }
        return switch (status.trim()) {
            case "Ho?t d?ng", "Ho?t ??ng", "Hoat dong", "Hoạt động" -> "Hoạt động";
            case "B?o tr?", "Bao tri", "Bảo trì" -> "Bảo trì";
            case "T?m ng?ng", "Tam ngung", "Tạm ngưng" -> "Tạm ngưng";
            default -> status.trim();
        };
    }
}
