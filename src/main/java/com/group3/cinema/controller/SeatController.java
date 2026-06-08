/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.controller;

import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.service.CatalogService;
import com.group3.cinema.service.RoomService;
import com.group3.cinema.service.SeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Controller xá»­ lÃ½ trang Thiáº¿t káº¿ sÆ¡ Ä‘á»“ gháº¿.
 * URL: /admin/rooms/{roomId}/seats
 */
@Controller
@RequestMapping("/admin/rooms/{roomId}/seats")
public class SeatController {

    private static final Logger log = LoggerFactory.getLogger(SeatController.class);

    private final SeatService seatService;
    private final RoomService  roomService;
    private final CatalogService catalogService;

    public SeatController(SeatService seatService, RoomService roomService, CatalogService catalogService) {
        this.seatService = seatService;
        this.roomService = roomService;
        this.catalogService = catalogService;
    }

    // â”€â”€â”€ GET: Trang thiáº¿t káº¿ sÆ¡ Ä‘á»“ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping
    public String seatDesignPage(@PathVariable("roomId") Long roomId, Model model) {

        Room room = roomService.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng id=" + roomId));

        // XÃ¢y dá»±ng ma tráº­n kiá»ƒu gháº¿ 2D (láº¥y tá»« DB hoáº·c máº·c Ä‘á»‹nh "std")
        String[][] matrix    = seatService.buildMatrix(roomId);
        String     matrixJson = seatService.matrixToJson(matrix);
        List<SeatType> seatTypes = catalogService.getActiveSeatTypes();

        // Thá»‘ng kÃª
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

    // â”€â”€â”€ POST: LÆ°u sÆ¡ Ä‘á»“ gháº¿ (nháº­n JSON tá»« fetch()) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Body JSON nháº­n vÃ o: { "matrix": [["std","vip",...], ["couple","skip",...], ...] }
     * Spring MVC + Jackson (cÃ³ sáºµn trong spring-boot-starter-web) tá»± parse thÃ nh Map.
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveMatrix(
            @PathVariable("roomId") Long roomId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new HashMap<>();
        try {
            String[][] matrix = parseMatrixBody(body);

            seatService.saveMatrix(roomId, matrix);

            result.put("success", true);
            result.put("message", "Lưu sơ đồ ghế thành công!");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Lỗi lưu sơ đồ ghế roomId={}", roomId, e);
            result.put("success", false);
            result.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    private String[][] parseMatrixBody(Map<String, Object> body) {
        if (body == null || !(body.get("matrix") instanceof List<?> rawMatrix) || rawMatrix.isEmpty()) {
            throw new IllegalArgumentException("Dữ liệu sơ đồ ghế không hợp lệ.");
        }

        int rows = rawMatrix.size();
        Object firstRow = rawMatrix.get(0);
        if (!(firstRow instanceof List<?> firstRowList) || firstRowList.isEmpty()) {
            throw new IllegalArgumentException("Sơ đồ ghế phải có ít nhất 1 cột.");
        }

        int cols = firstRowList.size();
        String[][] matrix = new String[rows][cols];
        for (int r = 0; r < rows; r++) {
            Object row = rawMatrix.get(r);
            if (!(row instanceof List<?> rowList)) {
                throw new IllegalArgumentException("Hàng " + (r + 1) + " của sơ đồ ghế không hợp lệ.");
            }
            if (rowList.size() != cols) {
                throw new IllegalArgumentException("Các hàng trong sơ đồ ghế phải có cùng số cột.");
            }
            for (int c = 0; c < cols; c++) {
                Object cell = rowList.get(c);
                if (!(cell instanceof String type)) {
                    throw new IllegalArgumentException("Loại ghế tại hàng " + (r + 1) + ", cột " + (c + 1) + " không hợp lệ.");
                }
                matrix[r][c] = type.trim();
            }
        }
        return matrix;
    }

    // â”€â”€â”€ POST: Reset vá» máº·c Ä‘á»‹nh (server-side) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/reset")
    public String resetMatrix(@PathVariable("roomId") Long roomId,
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
