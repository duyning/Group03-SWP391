package com.group3.cinema.dto;

/*
 * Added on 2026-06-24: Read model for one available customer showtime.
 * Created by: HuyPB - HE191335
 */

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingShowtimeView(
        Long id,
        LocalDate showDate,
        LocalTime startTime,
        LocalTime endTime,
        String roomName,
        String format,
        int availableSeatCount
) {
}
