/*
 * Updated on 2026-07-24: Added detailed seat-layout flow comments.
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
 * Controller điều phối trang thiết kế sơ đồ ghế của một phòng chiếu.
 *
 * URL base: `/admin/rooms/{roomId}/seats`
 *
 * Luồng tổng quan:
 * 1. Từ danh sách phòng `manager_room.html`, người quản lý bấm thiết kế ghế của một phòng cụ thể.
 * 2. Browser gọi GET `/admin/rooms/{roomId}/seats`.
 * 3. Controller lấy thông tin phòng qua `RoomService`, lấy ma trận ghế qua `SeatService`,
 *    lấy danh mục loại ghế active qua `CatalogService`.
 * 4. View `manager_seat.html` render công cụ vẽ sơ đồ ghế bằng JavaScript.
 * 5. Khi bấm lưu, JavaScript gửi JSON `{ "matrix": [["std","vip",...], ...] }`
 *    tới POST `/admin/rooms/{roomId}/seats/save`.
 * 6. Controller parse JSON thành `String[][]`, gọi `SeatService.saveMatrix(...)`.
 * 7. Service validate nghiệp vụ, xóa sơ đồ cũ, sinh lại toàn bộ bản ghi `seats`,
 *    cập nhật `rooms.rows`, `rooms.cols`, `rooms.total_seats`.
 */
@Controller
@RequestMapping("/admin/rooms/{roomId}/seats")
public class SeatController {

    private static final Logger log = LoggerFactory.getLogger(SeatController.class);

    private final SeatService seatService;
    private final RoomService roomService;
    private final CatalogService catalogService;

    public SeatController(SeatService seatService, RoomService roomService, CatalogService catalogService) {
        this.seatService = seatService;
        this.roomService = roomService;
        this.catalogService = catalogService;
    }

    /**
     * GET /admin/rooms/{roomId}/seats
     *
     * Luồng mở trang thiết kế:
     * - `roomId` lấy từ path để biết đang thiết kế sơ đồ cho phòng nào.
     * - Gọi `roomService.findById(roomId)` để lấy entity `Room`; nếu không có thì báo lỗi.
     * - Gọi `seatService.buildMatrix(roomId)`:
     *   + Nếu phòng chưa có ghế, service dựng ma trận mặc định theo `room.rows` x `room.cols`.
     *   + Nếu phòng đã có ghế, service lấy từ bảng `seats` và đổ về đúng vị trí row/col.
     * - Gọi `seatService.matrixToJson(matrix)` để đưa ma trận Java sang JSON cho JavaScript.
     * - Gọi `catalogService.getActiveSeatTypes()` để view có danh sách loại ghế đang dùng được.
     * - Tính thống kê nhanh theo loại ghế để hiển thị bên cạnh sơ đồ.
     * - Trả về view `manager_seat.html`.
     */
    @GetMapping
    public String seatDesignPage(@PathVariable("roomId") Long roomId, Model model) {

        Room room = roomService.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng id=" + roomId));

        String[][] matrix = seatService.buildMatrix(roomId);
        String matrixJson = seatService.matrixToJson(matrix);
        List<SeatType> seatTypes = catalogService.getActiveSeatTypes();

        /*
         * Các biến count này phục vụ phần thống kê giao diện.
         * Tổng sức chứa thực tế không chỉ là số ô: ghế couple có sức chứa 2,
         * ghế broken/empty/skip không được tính là ghế bán được.
         */
        long countStd = seatService.countByType(roomId, "std");
        long countVip = seatService.countByType(roomId, "vip");
        long countCouple = seatService.countByType(roomId, "couple");
        long countBroken = seatService.countByType(roomId, "broken");
        long totalCapacity = countStd + countVip + (countCouple * 2);

        model.addAttribute("room", room);
        model.addAttribute("matrixJson", matrixJson);
        model.addAttribute("countStd", countStd);
        model.addAttribute("countVip", countVip);
        model.addAttribute("countCouple", countCouple);
        model.addAttribute("countBroken", countBroken);
        model.addAttribute("totalCapacity", totalCapacity);
        model.addAttribute("hasExisting", seatService.hasSeats(roomId));
        model.addAttribute("seatTypes", seatTypes);
        model.addAttribute("seatTypesJson", catalogService.seatTypesToJson(seatTypes));

        return "manager_seat";
    }

    /**
     * POST /admin/rooms/{roomId}/seats/save
     *
     * Luồng lưu sơ đồ ghế:
     * - View gửi request bằng fetch/AJAX, content-type JSON.
     * - Body có dạng `{ "matrix": [["std","std","vip"], ["couple","skip","empty"]] }`.
     * - `parseMatrixBody(...)` kiểm tra JSON có đúng dạng mảng 2 chiều không, mọi hàng có cùng số cột không.
     * - `seatService.saveMatrix(roomId, matrix)` thực hiện nghiệp vụ lưu thật:
     *   + Validate roomId, kích thước ma trận, mã loại ghế, quy tắc ghế couple/skip.
     *   + Nếu phòng có lịch chiếu hiện tại/tương lai thì không cho đổi số hàng/cột.
     *   + Xóa toàn bộ ghế cũ trong bảng `seats` của phòng.
     *   + Sinh lại từng `Seat` theo rowIndex/colIndex/seatType/seatLabel.
     *   + Cập nhật tổng sức chứa vào bảng `rooms`.
     * - Controller trả JSON success/error để giao diện hiển thị thông báo mà không cần reload thô.
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

    /**
     * Chuyển body JSON thô do Jackson parse thành `Map<String,Object>` sang mảng `String[][]`.
     *
     * Lý do cần hàm này:
     * - JavaScript gửi mảng động, Spring parse thành `List<?>`.
     * - Service cần `String[][]` rõ kiểu để validate ma trận và lưu ghế.
     * - Hàm này bắt lỗi sớm các trường hợp: thiếu key `matrix`, ma trận rỗng,
     *   hàng không phải list, hàng lệch số cột, cell không phải string.
     */
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

    /**
     * POST /admin/rooms/{roomId}/seats/reset
     *
     * Luồng reset:
     * - Lấy kích thước hiện tại của phòng từ `rooms.rows` và `rooms.cols`.
     * - Tạo ma trận mới toàn bộ là `std`.
     * - Gọi lại đúng `seatService.saveMatrix(...)` để đi qua cùng một lớp validate/lưu dữ liệu.
     * - Vì dùng chung service nên vẫn bị chặn nếu thay đổi kích thước phòng đang có lịch chiếu sắp tới.
     */
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
