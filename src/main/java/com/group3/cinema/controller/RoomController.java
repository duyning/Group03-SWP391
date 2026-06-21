/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.controller;

import com.group3.cinema.entity.Room;
import com.group3.cinema.service.CatalogService;
import com.group3.cinema.service.RoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller xá»­ lÃ½ táº¥t cáº£ chá»©c nÄƒng Quáº£n lÃ½ PhÃ²ng chiáº¿u.
 * URL base: /admin/rooms
 */
@Controller
@RequestMapping("/admin/rooms")
public class RoomController {

    private final RoomService roomService;
    private final CatalogService catalogService;

    public RoomController(RoomService roomService, CatalogService catalogService) {
        this.roomService = roomService;
        this.catalogService = catalogService;
    }

    // ID ráº¡p máº·c Ä‘á»‹nh (Beta Cinemas ThÃ¡i NguyÃªn = 1)
    private static final Long DEFAULT_CINEMA_ID = 1L;

    // â”€â”€â”€ GET: Danh sÃ¡ch phÃ²ng (cÃ³ filter) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping
    public String listRooms(
            @RequestParam(value = "roomName",  required = false) String roomName,
            @RequestParam(value = "roomType",  required = false) String roomType,
            @RequestParam(value = "audioTech", required = false) String audioTech,
            @RequestParam(value = "status",    required = false) String status,
            @RequestParam(value = "minSeats",  required = false) Integer minSeats,
            Model model) {

        Long cinemaId = DEFAULT_CINEMA_ID;

        // Láº¥y danh sÃ¡ch phÃ²ng (cÃ³ Ã¡p dá»¥ng filter náº¿u cÃ³ tham sá»‘)
        List<Room> rooms;
        try {
            rooms = roomService.filterRooms(cinemaId, roomName, roomType, audioTech, status, minSeats);
        } catch (Exception e) {
            rooms = roomService.getAllRooms(cinemaId);
            model.addAttribute("errorMessage", "Bộ lọc không hợp lệ: " + e.getMessage());
            minSeats = null;
        }

        // Thá»‘ng kÃª cho stat cards
        model.addAttribute("totalRooms",      roomService.countTotal(cinemaId));
        model.addAttribute("activeRooms",     roomService.countActive(cinemaId));
        model.addAttribute("maintenanceRooms",roomService.countMaintenance(cinemaId));
        model.addAttribute("totalSeats",      roomService.sumTotalSeats(cinemaId));

        // Danh sÃ¡ch phÃ²ng Ä‘Ã£ lá»c
        model.addAttribute("rooms", rooms);

        // Giá»¯ láº¡i giÃ¡ trá»‹ filter trÃªn form
        model.addAttribute("filterRoomName", roomName);
        model.addAttribute("filterRoomType", roomType);
        model.addAttribute("filterAudioTech", audioTech);
        model.addAttribute("filterStatus",   status);
        model.addAttribute("filterMinSeats", minSeats);
        model.addAttribute("roomTypeOptions", catalogService.getActiveRoomTypes());
        model.addAttribute("audioTechOptions", catalogService.getActiveAudioTechnologies());

        // Object rá»—ng dÃ¹ng cho modal ThÃªm phÃ²ng
        model.addAttribute("newRoom", new Room());

        return "manager_room";
    }

    // â”€â”€â”€ POST: ThÃªm phÃ²ng má»›i â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/add")
    public String addRoom(
            @RequestParam("roomName")  String  roomName,
            @RequestParam(value = "roomTypes", required = false) List<String> roomTypes,
            @RequestParam("audioTech") String  audioTech,
            @RequestParam("status")    String  status,
            RedirectAttributes redirectAttributes) {

        try {
            roomService.addRoom(DEFAULT_CINEMA_ID, roomName, roomTypes, audioTech, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm phòng \"" + roomName + "\" thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Lỗi khi thêm phòng: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    // â”€â”€â”€ POST: Cáº­p nháº­t phÃ²ng â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/edit")
    public String editRoom(
            @RequestParam("id")        Long   id,
            @RequestParam("roomName")  String roomName,
            @RequestParam(value = "roomTypes", required = false) List<String> roomTypes,
            @RequestParam("audioTech") String audioTech,
            @RequestParam("status")    String status,
            RedirectAttributes redirectAttributes) {

        try {
            roomService.updateRoom(id, roomName, roomTypes, audioTech, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật phòng \"" + roomName + "\" thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Lỗi khi cập nhật: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    // â”€â”€â”€ POST: XÃ³a phÃ²ng â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
