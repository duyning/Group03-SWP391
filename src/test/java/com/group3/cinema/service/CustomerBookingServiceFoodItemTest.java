package com.group3.cinema.service;

import com.group3.cinema.dto.BookingSelection;
import com.group3.cinema.entity.BookingTicket;
import com.group3.cinema.entity.FoodItem;
import com.group3.cinema.repository.BookingComboRepository;
import com.group3.cinema.repository.BookingFoodItemRepository;
import com.group3.cinema.repository.BookingRepository;
import com.group3.cinema.repository.BookingTicketRepository;
import com.group3.cinema.repository.ComboRepository;
import com.group3.cinema.repository.FoodItemRepository;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerBookingServiceFoodItemTest {
    private FoodItemRepository foodItemRepository;
    private BookingTicketRepository bookingTicketRepository;
    private CustomerBookingService service;

    @BeforeEach
    void setUp() {
        foodItemRepository = mock(FoodItemRepository.class);
        bookingTicketRepository = mock(BookingTicketRepository.class);
        service = new CustomerBookingService(
                mock(ComboRepository.class),
                foodItemRepository,
                bookingTicketRepository,
                mock(BookingRepository.class),
                mock(BookingComboRepository.class),
                mock(BookingFoodItemRepository.class),
                mock(ShowtimeRepository.class),
                mock(VoucherRepository.class),
                mock(JdbcTemplate.class)
        );
    }

    @Test
    void acceptsActiveStandaloneFoodQuantities() {
        FoodItem popcorn = foodItem(7L, "Bắp rang", "ACTIVE");
        when(foodItemRepository.findAllById(any())).thenReturn(List.of(popcorn));

        LinkedHashMap<Long, Integer> selected = service.validateFoodItemQuantities(
                Map.of("food_7", "2", "combo_3", "1"));

        assertEquals(Map.of(7L, 2), selected);
    }

    @Test
    void rejectsInactiveStandaloneFood() {
        FoodItem popcorn = foodItem(7L, "Bắp rang", "INACTIVE");
        when(foodItemRepository.findAllById(any())).thenReturn(List.of(popcorn));

        assertThrows(IllegalArgumentException.class,
                () -> service.validateFoodItemQuantities(Map.of("food_7", "1")));
    }

    @Test
    void rejectsQuantityAboveBookingLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateFoodItemQuantities(Map.of("food_7", "11")));
    }

    @Test
    void includesStandaloneFoodInBookingTotal() {
        FoodItem popcorn = foodItem(7L, "Bắp rang", "ACTIVE");
        popcorn.setUnitPrice(new BigDecimal("30000"));
        BookingTicket ticket = new BookingTicket();
        ticket.setShowtimeId(12L);
        ticket.setPrice(new BigDecimal("100000"));
        ticket.setStatus(BookingTicket.Status.HOLDING);
        ticket.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(foodItemRepository.findAllById(any())).thenReturn(List.of(popcorn));
        when(bookingTicketRepository.findByHoldToken("hold-token")).thenReturn(List.of(ticket));
        BookingSelection selection = new BookingSelection(12L, 3, 4L, "Phim", "Phòng 1",
                LocalDate.now().plusDays(1), LocalTime.of(19, 0), LocalTime.of(21, 0), "2D");

        CustomerBookingService.BookingSummary summary = service.calculateSummary(
                selection, "hold-token", Map.of(), Map.of(7L, 2), null);

        assertEquals(new BigDecimal("60000"), summary.foodSubtotal());
        assertEquals(new BigDecimal("160000"), summary.total());
        assertEquals(1, summary.foodItems().size());
    }

    private FoodItem foodItem(Long id, String name, String status) {
        FoodItem item = new FoodItem();
        item.setId(id);
        item.setName(name);
        item.setStatus(status);
        return item;
    }
}
