/*
 * Updated on 2026-07-24: Added detailed room-management flow comments.
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
 * Controller điều phối toàn bộ màn hình "Danh mục phòng" của Admin/Manager.
 *
 * Luồng tổng quan chức năng tạo/cập nhật/xóa phòng:
 * 1. Người quản lý thao tác tại view `manager_room.html`.
 * 2. Form hoặc filter gửi request tới các endpoint dưới base URL `/admin/rooms`.
 * 3. Controller chỉ nhận dữ liệu HTTP, giữ lại flash message/model và gọi `RoomService`.
 * 4. `RoomService` thực hiện validate nghiệp vụ, kiểm tra danh mục loại phòng/âm thanh, kiểm tra trùng tên,
 *    kiểm tra ràng buộc lịch chiếu và ghi dữ liệu qua `RoomRepository`.
 * 5. Sau khi xử lý xong, controller redirect lại `/admin/rooms` để tải danh sách mới nhất.
 *
 * Lưu ý nghiệp vụ:
 * - Tạo phòng chưa tạo ghế ngay. Sau khi phòng được tạo, người quản lý vào
 *   `/admin/rooms/{roomId}/seats` để thiết kế sơ đồ ghế.
 * - Khi sơ đồ ghế được lưu, `SeatService` mới cập nhật `rooms.rows`, `rooms.cols`, `rooms.total_seats`.
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

    // ID rạp mặc định đang được project dùng khi chưa có màn chọn nhiều cụm rạp.
    private static final Long DEFAULT_CINEMA_ID = 1L;

    /**
     * GET /admin/rooms
     *
     * Luồng hiển thị danh sách phòng:
     * - Browser mở trang quản lý phòng hoặc submit bộ lọc.
     * - Controller gom các tham số filter: tên phòng, loại phòng, âm thanh, trạng thái, sức chứa tối thiểu.
     * - Gọi `roomService.filterRooms(...)` để lấy danh sách phòng phù hợp.
     * - Gọi các hàm thống kê của `RoomService` để tạo stat cards: tổng phòng, phòng hoạt động,
     *   phòng bảo trì, tổng sức chứa.
     * - Gọi `CatalogService` để lấy danh mục loại phòng và công nghệ âm thanh đang active,
     *   giúp dropdown trên form không bị nhập tay/fix cứng.
     * - Trả về `manager_room.html`.
     */
    @GetMapping
    public String listRooms(
            @RequestParam(value = "roomName",  required = false) String roomName,
            @RequestParam(value = "roomType",  required = false) String roomType,
            @RequestParam(value = "audioTech", required = false) String audioTech,
            @RequestParam(value = "status",    required = false) String status,
            @RequestParam(value = "minSeats",  required = false) Integer minSeats,
            Model model) {

        Long cinemaId = DEFAULT_CINEMA_ID;

        // Lấy danh sách phòng có áp dụng filter; nếu filter sai thì fallback về toàn bộ danh sách.
        List<Room> rooms;
        try {
            rooms = roomService.filterRooms(cinemaId, roomName, roomType, audioTech, status, minSeats);
        } catch (Exception e) {
            rooms = roomService.getAllRooms(cinemaId);
            model.addAttribute("errorMessage", "Bộ lọc không hợp lệ: " + e.getMessage());
            minSeats = null;
        }

        // Thống kê cho các thẻ tổng quan trên đầu trang.
        model.addAttribute("totalRooms",      roomService.countTotal(cinemaId));
        model.addAttribute("activeRooms",     roomService.countActive(cinemaId));
        model.addAttribute("maintenanceRooms",roomService.countMaintenance(cinemaId));
        model.addAttribute("totalSeats",      roomService.sumTotalSeats(cinemaId));

        // Danh sách phòng đã lọc, dùng để render bảng phòng.
        model.addAttribute("rooms", rooms);

        // Giữ lại giá trị filter để khi reload trang form vẫn hiển thị đúng lựa chọn hiện tại.
        model.addAttribute("filterRoomName", roomName);
        model.addAttribute("filterRoomType", roomType);
        model.addAttribute("filterAudioTech", audioTech);
        model.addAttribute("filterStatus",   status);
        model.addAttribute("filterMinSeats", minSeats);
        model.addAttribute("roomTypeOptions", catalogService.getActiveRoomTypes());
        model.addAttribute("audioTechOptions", catalogService.getActiveAudioTechnologies());

        // Object rỗng dùng cho modal thêm phòng.
        model.addAttribute("newRoom", new Room());

        return "manager_room";
    }

    /**
     * POST /admin/rooms/add
     *
     * Luồng tạo phòng:
     * - Form thêm phòng gửi `roomName`, nhiều `roomTypes`, `audioTech`, `status`.
     * - Controller không tự validate chi tiết mà chuyển sang `roomService.addRoom(...)`.
     * - `RoomService.addRoom(...)` chuẩn hóa tên, kiểm tra loại phòng/âm thanh phải tồn tại trong danh mục active,
     *   kiểm tra trùng tên trong cùng rạp và chặn tạo phòng ở trạng thái "Hoạt động" khi chưa có sơ đồ ghế.
     * - Nếu hợp lệ, service tạo entity `Room` với `totalSeats = 0`; rows/cols dùng giá trị mặc định entity
     *   cho tới khi người quản lý lưu sơ đồ ghế.
     * - Kết quả được trả bằng flash message rồi redirect về danh sách phòng.
     */
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

    /**
     * POST /admin/rooms/edit
     *
     * Luồng cập nhật phòng:
     * - Form sửa phòng gửi `id` và các thông tin mới.
     * - Controller gọi `roomService.updateRoom(...)`.
     * - Service lấy phòng hiện tại từ DB, validate lại toàn bộ dữ liệu giống tạo mới,
     *   kiểm tra tên mới không trùng phòng khác trong cùng rạp.
     * - Nếu phòng đang có lịch chiếu hiện tại/tương lai thì không cho đổi tên,
     *   vì bảng lịch chiếu hiện đang lưu tên phòng để hiển thị/đối chiếu.
     * - Nếu muốn bật trạng thái "Hoạt động", phòng bắt buộc đã có sơ đồ ghế và `totalSeats > 0`.
     * - Service lưu lại entity `Room`, controller redirect về list.
     */
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

    /**
     * POST /admin/rooms/delete
     *
     * Luồng xóa phòng:
     * - Form gửi `id` phòng cần xóa.
     * - `RoomService.deleteRoom(...)` kiểm tra phòng tồn tại và không có lịch chiếu hiện tại/tương lai.
     * - Nếu phòng còn lịch chiếu, service yêu cầu chuyển trạng thái sang "Tạm ngưng" hoặc "Bảo trì" thay vì xóa.
     * - Nếu được phép xóa, service xóa toàn bộ ghế thuộc phòng qua `SeatRepository.deleteAllByRoomId(id)`,
     *   sau đó xóa bản ghi `rooms`.
     */
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
