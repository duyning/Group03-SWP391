package com.group3.cinema.repository;

import com.group3.cinema.entity.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboItemRepository extends JpaRepository<ComboItem, Long> {

    boolean existsByFoodItemId(Long foodItemId);
}