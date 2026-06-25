package com.group3.cinema.dto;

/*
 * Added on 2026-06-24: Groups available showtimes by show date for customer UI.
 * Created by: HuyPB - HE191335
 */

import java.time.LocalDate;
import java.util.List;

public record BookingShowtimeDateView(
        LocalDate date,
        String dayOfWeekLabel,
        List<BookingShowtimeView> showtimes
) {
}
