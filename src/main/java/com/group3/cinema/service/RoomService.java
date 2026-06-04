/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Room;
import com.group3.cinema.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    // â”€â”€â”€ Láº¥y danh sÃ¡ch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Láº¥y táº¥t cáº£ phÃ²ng (khÃ´ng lá»c) */
    public List<Room> getAllRooms(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId);
    }

    /**
     * Lá»c phÃ²ng theo cÃ¡c tiÃªu chÃ­ tÃ¬m kiáº¿m.
     * Chuá»—i rá»—ng Ä‘Æ°á»£c coi lÃ  "khÃ´ng lá»c" (null).
     */
    public List<Room> filterRooms(Long cinemaId, String roomName, String roomType,
                                   String audioTech, String status, Integer minSeats) {
        String roomNameFilter = StringUtils.hasText(roomName) ? roomName.trim().toLowerCase() : null;
        String roomTypeFilter = StringUtils.hasText(roomType) ? roomType.trim().toLowerCase() : null;
        String audioTechFilter = StringUtils.hasText(audioTech) ? audioTech.trim().toLowerCase() : null;
        String statusFilter = normalizeStatus(status);

        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> roomNameFilter == null
                        || room.getRoomName().toLowerCase().contains(roomNameFilter))
                .filter(room -> roomTypeFilter == null
                        || room.getRoomType().toLowerCase().contains(roomTypeFilter))
                .filter(room -> audioTechFilter == null
                        || room.getAudioTech().toLowerCase().contains(audioTechFilter))
                .filter(room -> statusFilter == null
                        || statusFilter.equals(room.getStatus()))
                .filter(room -> minSeats == null
                        || room.getTotalSeats() >= minSeats)
                .toList();
    }

    /** TÃ¬m 1 phÃ²ng theo id */
    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id)
                .map(room -> {
                    normalizeRoomStatus(room);
                    return room;
                });
    }

    // â”€â”€â”€ Thá»‘ng kÃª â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public long countTotal(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).size();
    }

    public long countActive(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> "Hoáº¡t Ä‘á»™ng".equals(room.getStatus()))
                .count();
    }

    public long countMaintenance(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .peek(this::normalizeRoomStatus)
                .filter(room -> "Báº£o trÃ¬".equals(room.getStatus()))
                .count();
    }

    public long sumTotalSeats(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .mapToLong(Room::getTotalSeats)
                .sum();
    }

    public List<String> getRoomTypeOptions(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .map(Room::getRoomType)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public List<String> getAudioTechOptions(Long cinemaId) {
        return roomRepository.findByCinemaId(cinemaId).stream()
                .map(Room::getAudioTech)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    // â”€â”€â”€ CRUD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Transactional
    public Room save(Room room) {
        return roomRepository.save(room);
    }

    @Transactional
    public Room addRoom(Long cinemaId, String roomName, String roomType,
                        String audioTech, String status) {
        Room room = Room.builder()
                .cinemaId(cinemaId)
                .roomName(roomName.trim())
                .roomType(roomType.trim())
                .audioTech(audioTech.trim())
                .totalSeats(0)
                .status(normalizeStatus(status))
                .build();
        return roomRepository.save(room);
    }

    @Transactional
    public Room updateRoom(Long id, String roomName, String roomType,
                           String audioTech, String status) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y phÃ²ng id=" + id));
        room.setRoomName(roomName.trim());
        room.setRoomType(roomType.trim());
        room.setAudioTech(audioTech.trim());
        room.setStatus(normalizeStatus(status));
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }

    private void normalizeRoomStatus(Room room) {
        room.setStatus(normalizeStatus(room.getStatus()));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String trimmed = status.trim();
        return switch (trimmed) {
            case "Ho?t d?ng", "Ho?t ??ng", "Hoat dong", "Hoáº¡t Ä‘á»™ng" -> "Hoáº¡t Ä‘á»™ng";
            case "B?o tr?", "Bao tri", "Báº£o trÃ¬" -> "Báº£o trÃ¬";
            case "T?m ng?ng", "Tam ngung", "Táº¡m ngÆ°ng" -> "Táº¡m ngÆ°ng";
            default -> trimmed;
        };
    }
}
