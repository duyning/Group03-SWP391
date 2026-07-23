package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.Seat;
import com.group3.cinema.entity.SeatType;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.SeatRepository;
import com.group3.cinema.repository.SeatTypeRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldingServiceTest {

    @Mock
    private SeatRepository seatRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BookingTicketRepository ticketRepository;
    @Mock
    private SeatTypeRepository seatTypeRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private TicketService ticketService;

    private SeatHoldingService service;
    private BookingSelection selection;

    @BeforeEach
    void setUp() {
        service = new SeatHoldingService(seatRepository, roomRepository, ticketRepository,
                seatTypeRepository, showtimeRepository, ticketService);
        selection = new BookingSelection(12L, 7, 3L, "Phim", "Phòng 03",
                LocalDate.now().plusDays(1), LocalTime.of(19, 0), LocalTime.of(21, 0), "2D");

        SeatType standard = new SeatType(1L, "std", "Ghế thường", "#e2e8f0", 1, true, true);
        SeatType couple = new SeatType(2L, "couple", "Ghế đôi", "#fbcfe8", 2, true, true);
        when(seatTypeRepository.findAllByOrderByIdAsc()).thenReturn(List.of(standard, couple));
        when(showtimeRepository.findById(12L)).thenReturn(Optional.of(new Showtime()));
    }

    @Test
    void countsOneCoupleSeatAsTwoWhenEnforcingBookingLimit() {
        List<Seat> seats = new ArrayList<>();
        seats.add(seat(1L, "couple"));
        for (long id = 2; id <= 8; id++) {
            seats.add(seat(id, "std"));
        }
        when(seatRepository.findAllById(any())).thenReturn(seats);

        assertThatThrownBy(() -> service.holdSeats(selection,
                seats.stream().map(Seat::getId).toList(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghế đôi")
                .hasMessageContaining("2 ghế");
    }

    @Test
    void allowsFourCoupleSeatsBecauseTheyRepresentEightSeats() {
        List<Seat> seats = List.of(
                seat(1L, "couple"), seat(2L, "couple"),
                seat(3L, "couple"), seat(4L, "couple")
        );
        when(seatRepository.findAllById(any())).thenReturn(seats);
        when(ticketRepository.findByShowtimeIdAndSeatIdIn(any(), any())).thenReturn(List.of());

        SeatHoldingService.HoldResult result = service.holdSeats(selection,
                seats.stream().map(Seat::getId).toList(), null);

        assertThat(result.tickets()).hasSize(4);
    }

    private Seat seat(Long id, String type) {
        return new Seat(id, 3L, 0, id.intValue() - 1, "A" + id, type);
    }
}
