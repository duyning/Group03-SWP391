package com.group3.cinema.repository;

/*
 * Created on 2026-06-21: Repository for combo item links.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboItemRepository extends JpaRepository<ComboItem, Long> {

    boolean existsByFoodItemId(Long foodItemId);
}
