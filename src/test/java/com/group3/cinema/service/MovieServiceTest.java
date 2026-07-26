package com.group3.cinema.service;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieService movieService;

    @Test
    void getsTopFiveMoviesByPaidTicketRevenueInTheLastFourteenDays() {
        Movie movie = new Movie();
        movie.setId(7);
        movie.setTitle("Phim bán chạy");
        List<Movie> expected = List.of(movie);
        when(movieRepository.findHotMoviesByTicketRevenue(any(), any(), any()))
                .thenReturn(expected);

        LocalDateTime beforeCall = LocalDateTime.now();
        List<Movie> result = movieService.getHotMovies();
        LocalDateTime afterCall = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(movieRepository).findHotMoviesByTicketRevenue(
                fromCaptor.capture(),
                toCaptor.capture(),
                pageableCaptor.capture()
        );

        assertThat(result).isSameAs(expected);
        assertThat(Duration.between(fromCaptor.getValue(), toCaptor.getValue()))
                .isEqualTo(Duration.ofDays(14));
        assertThat(toCaptor.getValue()).isBetween(beforeCall, afterCall);
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }
}
