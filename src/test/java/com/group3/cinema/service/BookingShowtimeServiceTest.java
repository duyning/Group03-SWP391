package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.dto.BookingShowtimeView;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.RoomRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingShowtimeServiceTest {

    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BookingTicketRepository ticketRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private BookingShowtimeService service;
    private Movie movie;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new BookingShowtimeService(showtimeRepository, movieRepository, roomRepository, ticketRepository,
                jdbcTemplate);
        movie = new Movie();
        movie.setId(7);
        movie.setTitle("Phim thử nghiệm");
        movie.setDuration(105);
        movie.setFormat("2D");
        movie.setActive(true);
        movie.setStatus(Movie.MovieStatus.NOW_SHOWING);

        room = new Room();
        room.setId(3L);
        room.setCinemaId(1L);
        room.setRoomName("Phòng 03");
        room.setStatus("Hoạt động");
        room.setTotalSeats(80);
    }

    @Test
    void returnsOnlyFutureShowtimesInAnActiveRoom() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Showtime showtime = showtime(12L, tomorrow, LocalTime.of(18, 0));
        when(movieRepository.findByIdAndActiveTrue(7)).thenReturn(Optional.of(movie));
        when(showtimeRepository.searchShowtimes(7, null, tomorrow, tomorrow))
                .thenReturn(List.of(showtime));
        when(roomRepository.findFirstByRoomNameIgnoreCase("Phòng 03"))
                .thenReturn(Optional.of(room));

        List<BookingShowtimeView> result = service.getAvailableShowtimes(7, tomorrow);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(12L);
            assertThat(view.endTime()).isEqualTo(LocalTime.of(19, 45));
            assertThat(view.availableSeatCount()).isEqualTo(80);
        });
    }

    @Test
    void storesValidatedShowtimeDataForTheNextBookingStep() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Showtime showtime = showtime(12L, tomorrow, LocalTime.of(18, 0));
        when(movieRepository.findByIdAndActiveTrue(7)).thenReturn(Optional.of(movie));
        when(showtimeRepository.findById(12L)).thenReturn(Optional.of(showtime));
        when(roomRepository.findFirstByRoomNameIgnoreCase("Phòng 03"))
                .thenReturn(Optional.of(room));

        BookingSelection selection = service.validateAndCreateSelection(12L, 7, tomorrow);

        assertThat(selection.showtimeId()).isEqualTo(12L);
        assertThat(selection.roomId()).isEqualTo(3L);
        assertThat(selection.movieTitle()).isEqualTo("Phim thử nghiệm");
    }

    @Test
    void rejectsShowtimeThatDoesNotMatchSelectedDate() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Showtime showtime = showtime(12L, tomorrow, LocalTime.of(18, 0));
        when(movieRepository.findByIdAndActiveTrue(7)).thenReturn(Optional.of(movie));
        when(showtimeRepository.findById(12L)).thenReturn(Optional.of(showtime));
        assertThatThrownBy(() -> service.validateAndCreateSelection(
                12L, 7, tomorrow.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không khớp");
    }

    private Showtime showtime(Long id, LocalDate date, LocalTime time) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovie(movie);
        showtime.setShowDate(date);
        showtime.setShowTime(time);
        showtime.setRoom("Phòng 03");
        return showtime;
    }
}
