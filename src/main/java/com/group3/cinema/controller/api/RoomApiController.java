/*
 * Updated on 2026-06-05: Expose room list for showtime room selection.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.controller.api;

import com.group3.cinema.entity.Room;
import com.group3.cinema.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomApiController {

    private static final Long DEFAULT_CINEMA_ID = 1L;

    private final RoomService roomService;

    @GetMapping
    public List<RoomOption> listRooms() {
        return roomService.filterRooms(DEFAULT_CINEMA_ID, null, null, null, "Hoạt động", null).stream()
                .map(room -> new RoomOption(
                        room.getId(),
                        room.getRoomName(),
                        room.getRoomType(),
                        room.getAudioTech(),
                        room.getStatus(),
                        room.getTotalSeats()))
                .toList();
    }

    public record RoomOption(
            Long id,
            String roomName,
            String roomType,
            String audioTech,
            String status,
            int totalSeats) {
    }
}
