package com.group3.cinema.dto;

/*
 * Added on 2026-06-24: Session-safe selected showtime data for booking flow.
 * Created by: HuyPB - HE191335
 */

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingSelection(
        Long showtimeId,
        Integer movieId,
        Long roomId,
        String movieTitle,
        String roomName,
        LocalDate showDate,
        LocalTime startTime,
        LocalTime endTime,
        String format
) implements Serializable {
}
