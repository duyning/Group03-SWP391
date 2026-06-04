/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package example.controller;

import example.entity.Room;
import example.service.CatalogService;
import example.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller xử lý tất cả chức năng Quản lý Phòng chiếu.
 * URL base: /admin/rooms
 */
@Controller
@RequestMapping("/admin/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final CatalogService catalogService;

    // ID rạp mặc định (Beta Cinemas Thái Nguyên = 1)
    private static final Long DEFAULT_CINEMA_ID = 1L;

    // ─── GET: Danh sách phòng (có filter) ─────────────────────────────────

    @GetMapping
    public String listRooms(
            @RequestParam(value = "roomName",  required = false) String roomName,
            @RequestParam(value = "roomType",  required = false) String roomType,
            @RequestParam(value = "audioTech", required = false) String audioTech,
            @RequestParam(value = "status",    required = false) String status,
            @RequestParam(value = "minSeats",  required = false) Integer minSeats,
            Model model) {

        Long cinemaId = DEFAULT_CINEMA_ID;

        // Lấy danh sách phòng (có áp dụng filter nếu có tham số)
        List<Room> rooms = roomService.filterRooms(cinemaId, roomName, roomType, audioTech, status, minSeats);

        // Thống kê cho stat cards
        model.addAttribute("totalRooms",      roomService.countTotal(cinemaId));
        model.addAttribute("activeRooms",     roomService.countActive(cinemaId));
        model.addAttribute("maintenanceRooms",roomService.countMaintenance(cinemaId));
        model.addAttribute("totalSeats",      roomService.sumTotalSeats(cinemaId));

        // Danh sách phòng đã lọc
        model.addAttribute("rooms", rooms);

        // Giữ lại giá trị filter trên form
        model.addAttribute("filterRoomName", roomName);
        model.addAttribute("filterRoomType", roomType);
        model.addAttribute("filterAudioTech", audioTech);
        model.addAttribute("filterStatus",   status);
        model.addAttribute("filterMinSeats", minSeats);
        model.addAttribute("roomTypeOptions", catalogService.getActiveRoomTypes());
        model.addAttribute("audioTechOptions", catalogService.getActiveAudioTechnologies());

        // Object rỗng dùng cho modal Thêm phòng
        model.addAttribute("newRoom", new Room());

        return "manager_room";
    }

    // ─── POST: Thêm phòng mới ──────────────────────────────────────────────

    @PostMapping("/add")
    public String addRoom(
            @RequestParam("roomName")  String  roomName,
            @RequestParam("roomType")  String  roomType,
            @RequestParam("audioTech") String  audioTech,
            @RequestParam("status")    String  status,
            RedirectAttributes redirectAttributes) {

        try {
            roomService.addRoom(DEFAULT_CINEMA_ID, roomName, roomType, audioTech, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm phòng \"" + roomName + "\" thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Lỗi khi thêm phòng: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    // ─── POST: Cập nhật phòng ─────────────────────────────────────────────

    @PostMapping("/edit")
    public String editRoom(
            @RequestParam("id")        Long   id,
            @RequestParam("roomName")  String roomName,
            @RequestParam("roomType")  String roomType,
            @RequestParam("audioTech") String audioTech,
            @RequestParam("status")    String status,
            RedirectAttributes redirectAttributes) {

        try {
            roomService.updateRoom(id, roomName, roomType, audioTech, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật phòng \"" + roomName + "\" thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Lỗi khi cập nhật: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    // ─── POST: Xóa phòng ─────────────────────────────────────────────────

    @PostMapping("/delete")
    public String deleteRoom(
            @RequestParam("id") Long id,
            RedirectAttributes redirectAttributes) {

        try {
            roomService.deleteRoom(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xóa phòng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Lỗi khi xóa phòng: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }
}
