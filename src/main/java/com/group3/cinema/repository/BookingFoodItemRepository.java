package com.group3.cinema.repository;

import com.group3.cinema.entity.BookingFoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingFoodItemRepository extends JpaRepository<BookingFoodItem, Long> {
    List<BookingFoodItem> findByBookingId(Long bookingId);
    void deleteByBookingId(Long bookingId);
}
