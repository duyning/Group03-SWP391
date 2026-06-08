/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.SeatTypeRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SeatService {

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 26;
    private static final int MIN_COLS = 1;
    private static final int MAX_COLS = 50;
    private static final int SEAT_TYPE_MAX_LENGTH = 30;
    private static final String SKIP_TYPE = "skip";

    private final SeatRepository seatRepository;
    private final RoomRepository  roomRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final ShowtimeRepository showtimeRepository;

    public SeatService(SeatRepository seatRepository,
                       RoomRepository roomRepository,
                       SeatTypeRepository seatTypeRepository,
                       ShowtimeRepository showtimeRepository) {
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.showtimeRepository = showtimeRepository;
    }

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // â”€â”€â”€ Äá»c sÆ¡ Ä‘á»“ gháº¿ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Láº¥y toÃ n bá»™ gháº¿ cá»§a phÃ²ng, Ä‘Ã£ sáº¯p xáº¿p theo (rowIndex, colIndex).
     * Náº¿u phÃ²ng chÆ°a cÃ³ sÆ¡ Ä‘á»“ â†’ tráº£ vá» danh sÃ¡ch rá»—ng (frontend sáº½ dÃ¹ng
     * initData() táº¡o máº·c Ä‘á»‹nh).
     */
    public List<Seat> getSeatsForRoom(Long roomId) {
        validateRoomId(roomId);
        return seatRepository.findByRoomIdOrderByRowIndexAscColIndexAsc(roomId);
    }

    /**
     * Chuyá»ƒn danh sÃ¡ch Seat thÃ nh máº£ng 2D String (chá»‰ chá»©a seatType),
     * kÃ­ch thÆ°á»›c [rows][cols] láº¥y tá»« Room entity.
     * DÃ¹ng Ä‘á»ƒ truyá»n sang JavaScript qua Thymeleaf (JSON).
     */
    public String[][] buildMatrix(Long roomId) {
        validateRoomId(roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        int rows = room.getRows();
        int cols = room.getCols();
        validateGridSize(rows, cols);
        String[][] matrix = new String[rows][cols];

        // Khá»Ÿi táº¡o máº·c Ä‘á»‹nh "std"
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                matrix[r][c] = "std";

        // Ghi Ä‘Ã¨ theo dá»¯ liá»‡u Ä‘Ã£ lÆ°u
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
     * Chuyá»ƒn matrix 2D thÃ nh chuá»—i JSON Ä‘á»ƒ embed vÃ o script Thymeleaf.
     * VÃ­ dá»¥: [["std","vip"],["couple","skip"]]
     */
    public String matrixToJson(String[][] matrix) {
        List<String> errors = validateMatrix(matrix);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
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

    // â”€â”€â”€ LÆ°u sÆ¡ Ä‘á»“ gháº¿ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * LÆ°u toÃ n bá»™ sÆ¡ Ä‘á»“ gháº¿:
     * 1. XÃ³a táº¥t cáº£ gháº¿ cÅ© cá»§a phÃ²ng
     * 2. Táº¡o má»›i theo matrixJson gá»­i tá»« trÃ¬nh duyá»‡t
     *
     * @param roomId     ID phÃ²ng
     * @param matrixJson JSON 2D máº£ng type: [["std","vip",...], ...]
     */
    @Transactional
    public void saveMatrix(Long roomId, String[][] matrixJson) {
        validateRoomId(roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
        List<String> errors = validateMatrix(matrixJson);
        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join(" ", errors));
        }
        validateLayoutChangeAllowed(room, matrixJson);

        // XÃ³a sÆ¡ Ä‘á»“ cÅ©
        seatRepository.deleteAllByRoomId(roomId);

        int rows = matrixJson.length;
        List<Seat> newSeats = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            int cols = matrixJson[r].length;
            for (int c = 0; c < cols; c++) {
                String seatType = matrixJson[r][c].trim();
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
                .filter(seat -> !SKIP_TYPE.equals(seat.getSeatType()))
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
        if (matrix.length < MIN_ROWS || matrix.length > MAX_ROWS) {
            errors.add("Sơ đồ ghế phải có từ " + MIN_ROWS + " đến " + MAX_ROWS + " hàng.");
        }

        int cols = matrix[0] == null ? 0 : matrix[0].length;
        if (cols == 0) {
            errors.add("Sơ đồ ghế phải có ít nhất 1 cột.");
            return errors;
        }
        if (cols < MIN_COLS || cols > MAX_COLS) {
            errors.add("Sơ đồ ghế phải có từ " + MIN_COLS + " đến " + MAX_COLS + " cột.");
        }

        List<SeatType> activeSeatTypes = seatTypeRepository.findByActiveTrueOrderByIdAsc();
        if (activeSeatTypes.isEmpty()) {
            errors.add("Chưa có loại ghế đang hoạt động. Vui lòng cấu hình loại ghế trước.");
            return errors;
        }

        Set<String> allowedTypes = activeSeatTypes.stream()
                .map(SeatType::getCode)
                .collect(Collectors.toSet());
        Map<String, Integer> capacityByType = activeSeatTypes.stream()
                .collect(Collectors.toMap(SeatType::getCode, SeatType::getCapacity, (a, b) -> a));
        allowedTypes.add(SKIP_TYPE);
        Map<String, Integer> orphanSkipByRow = new HashMap<>();
        int totalCapacity = 0;

        for (int r = 0; r < matrix.length; r++) {
            if (matrix[r] == null || matrix[r].length != cols) {
                errors.add("Các hàng trong sơ đồ ghế phải có cùng số cột.");
                break;
            }
            for (int c = 0; c < cols; c++) {
                String type = matrix[r][c] == null ? null : matrix[r][c].trim();
                if (type == null || type.isEmpty()) {
                    errors.add("Loại ghế không được để trống tại hàng " + rowName(r) + ", cột " + (c + 1) + ".");
                    continue;
                }
                if (type.length() > SEAT_TYPE_MAX_LENGTH) {
                    errors.add("Mã loại ghế tại hàng " + rowName(r) + ", cột " + (c + 1) + " vượt quá " + SEAT_TYPE_MAX_LENGTH + " ký tự.");
                    continue;
                }
                if (!type.matches("^[a-z0-9_]+$")) {
                    errors.add("Mã loại ghế tại hàng " + rowName(r) + ", cột " + (c + 1) + " chỉ được chứa chữ thường, số và dấu gạch dưới.");
                    continue;
                }
                if (!allowedTypes.contains(type)) {
                    errors.add("Loại ghế không hợp lệ tại hàng " + rowName(r) + ", cột " + (c + 1) + ".");
                    continue;
                }
                if ("couple".equals(type)) {
                    if (c >= cols - 1) {
                        errors.add("Ghế couple không được nằm ở cột cuối hàng " + rowName(r) + ".");
                    } else if (!SKIP_TYPE.equals(matrix[r][c + 1] == null ? null : matrix[r][c + 1].trim())) {
                        errors.add("Ghế couple tại " + rowName(r) + (c + 1) + " phải chiếm ô bên phải.");
                    }
                }
                if (SKIP_TYPE.equals(type) && (c == 0 || !"couple".equals(matrix[r][c - 1] == null ? null : matrix[r][c - 1].trim()))) {
                    orphanSkipByRow.put(rowName(r), orphanSkipByRow.getOrDefault(rowName(r), 0) + 1);
                }
                if (!SKIP_TYPE.equals(type)) {
                    totalCapacity += capacityByType.getOrDefault(type, 0);
                }
            }
        }

        orphanSkipByRow.forEach((row, count) ->
                errors.add("Hàng " + row + " có ô skip lẻ, không thuộc ghế couple."));
        if (totalCapacity <= 0) {
            errors.add("Sơ đồ ghế phải có ít nhất 1 ghế có sức chứa.");
        }
        return errors;
    }

    private String rowName(int rowIndex) {
        return rowIndex < ALPHABET.length() ? String.valueOf(ALPHABET.charAt(rowIndex)) : "?";
    }

    /** Sinh nhÃ£n gháº¿ dá»±a theo loáº¡i */
    private String buildLabel(String type, String rowLetter, int c,
                               int totalCols, String[] rowTypes) {
        return switch (type) {
            case "couple" -> rowLetter + (c + 1) + "-" + rowLetter + (c + 2);
            case "empty", "skip", "broken" -> "";
            default -> rowLetter + (c + 1); // std / vip
        };
    }

    // â”€â”€â”€ Thá»‘ng kÃª â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public long countByType(Long roomId, String type) {
        validateRoomId(roomId);
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Loại ghế thống kê không hợp lệ.");
        }
        return seatRepository.countByRoomIdAndSeatType(roomId, type);
    }

    public boolean hasSeats(Long roomId) {
        validateRoomId(roomId);
        return seatRepository.existsByRoomId(roomId);
    }

    private void validateRoomId(Long roomId) {
        if (roomId == null || roomId <= 0) {
            throw new IllegalArgumentException("ID phòng không hợp lệ.");
        }
    }

    private void validateGridSize(int rows, int cols) {
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("Số hàng ghế phải từ " + MIN_ROWS + " đến " + MAX_ROWS + ".");
        }
        if (cols < MIN_COLS || cols > MAX_COLS) {
            throw new IllegalArgumentException("Số cột ghế phải từ " + MIN_COLS + " đến " + MAX_COLS + ".");
        }
    }

    private void validateLayoutChangeAllowed(Room room, String[][] matrix) {
        boolean sizeChanged = room.getRows() != matrix.length
                || room.getCols() != (matrix.length > 0 ? matrix[0].length : 0);
        if (!sizeChanged) {
            return;
        }
        boolean hasUpcomingShowtimes = showtimeRepository
                .existsByRoomIgnoreCaseAndShowDateGreaterThanEqual(room.getRoomName(), LocalDate.now());
        if (hasUpcomingShowtimes) {
            throw new IllegalArgumentException("Phòng đang có lịch chiếu hiện tại hoặc tương lai nên không thể đổi số hàng/cột ghế.");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return status;
        }
        return switch (status.trim()) {
            case "Ho?t d?ng", "Ho?t ??ng", "Hoat dong", "Hoáº¡t Ä‘á»™ng", "Hoạt động" -> "Hoạt động";
            case "B?o tr?", "Bao tri", "Báº£o trÃ¬", "Bảo trì" -> "Bảo trì";
            case "T?m ng?ng", "Tam ngung", "Táº¡m ngÆ°ng", "Tạm ngưng" -> "Tạm ngưng";
            default -> status.trim();
        };
    }
}
