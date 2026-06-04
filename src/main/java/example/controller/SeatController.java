package example.controller;

import example.entity.Room;
import example.entity.SeatType;
import example.service.CatalogService;
import example.service.RoomService;
import example.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller xử lý trang Thiết kế sơ đồ ghế.
 * URL: /admin/rooms/{roomId}/seats
 */
@Controller
@RequestMapping("/admin/rooms/{roomId}/seats")
@RequiredArgsConstructor
@Slf4j
public class SeatController {

    private final SeatService seatService;
    private final RoomService  roomService;
    private final CatalogService catalogService;

    // ─── GET: Trang thiết kế sơ đồ ───────────────────────────────────────────

    @GetMapping
    public String seatDesignPage(@PathVariable Long roomId, Model model) {

        Room room = roomService.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng id=" + roomId));

        // Xây dựng ma trận kiểu ghế 2D (lấy từ DB hoặc mặc định "std")
        String[][] matrix    = seatService.buildMatrix(roomId);
        String     matrixJson = seatService.matrixToJson(matrix);
        List<SeatType> seatTypes = catalogService.getActiveSeatTypes();

        // Thống kê
        long countStd      = seatService.countByType(roomId, "std");
        long countVip      = seatService.countByType(roomId, "vip");
        long countCouple   = seatService.countByType(roomId, "couple");
        long countBroken   = seatService.countByType(roomId, "broken");
        long totalCapacity = countStd + countVip + (countCouple * 2);

        model.addAttribute("room",          room);
        model.addAttribute("matrixJson",    matrixJson);
        model.addAttribute("countStd",      countStd);
        model.addAttribute("countVip",      countVip);
        model.addAttribute("countCouple",   countCouple);
        model.addAttribute("countBroken",   countBroken);
        model.addAttribute("totalCapacity", totalCapacity);
        model.addAttribute("hasExisting",   seatService.hasSeats(roomId));
        model.addAttribute("seatTypes",     seatTypes);
        model.addAttribute("seatTypesJson", catalogService.seatTypesToJson(seatTypes));

        return "manager_seat";
    }

    // ─── POST: Lưu sơ đồ ghế (nhận JSON từ fetch()) ──────────────────────────

    /**
     * Body JSON nhận vào: { "matrix": [["std","vip",...], ["couple","skip",...], ...] }
     * Spring MVC + Jackson (có sẵn trong spring-boot-starter-web) tự parse thành Map.
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveMatrix(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new HashMap<>();
        try {
            // body.get("matrix") → List<List<String>> (Jackson tự cast)
            @SuppressWarnings("unchecked")
            List<List<String>> rawMatrix = (List<List<String>>) body.get("matrix");

            if (rawMatrix == null || rawMatrix.isEmpty()) {
                result.put("success", false);
                result.put("message", "Dữ liệu matrix không hợp lệ.");
                return ResponseEntity.badRequest().body(result);
            }

            // Chuyển List<List<String>> → String[][]
            int rows = rawMatrix.size();
            int cols = rawMatrix.get(0).size();
            String[][] matrix = new String[rows][cols];
            for (int r = 0; r < rows; r++) {
                List<String> rowList = rawMatrix.get(r);
                for (int c = 0; c < cols; c++) {
                    matrix[r][c] = rowList.get(c);
                }
            }

            seatService.saveMatrix(roomId, matrix);

            result.put("success", true);
            result.put("message", "Lưu sơ đồ ghế thành công!");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Lỗi lưu sơ đồ ghế roomId={}", roomId, e);
            result.put("success", false);
            result.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // ─── POST: Reset về mặc định (server-side) ───────────────────────────────

    @PostMapping("/reset")
    public String resetMatrix(@PathVariable Long roomId,
                              RedirectAttributes redirectAttributes) {
        try {
            Room room = roomService.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));
            String[][] defaultMatrix = new String[room.getRows()][room.getCols()];
            for (String[] row : defaultMatrix) Arrays.fill(row, "std");
            seatService.saveMatrix(roomId, defaultMatrix);
            redirectAttributes.addFlashAttribute("successMessage", "Đã reset sơ đồ ghế về mặc định!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi reset: " + e.getMessage());
        }
        return "redirect:/admin/rooms/" + roomId + "/seats";
    }
}
