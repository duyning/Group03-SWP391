package com.group3.cinema.repository;

/*
 * Created on 2026-06-21: Repository for combo food item catalog management.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<FoodItem> findByStatusInOrderByNameAsc(Collection<String> statuses);

    @Query("SELECT f FROM FoodItem f WHERE " +
            "(:keyword IS NULL OR f.name LIKE %:keyword% OR f.category LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR f.status = :status) " +
            "ORDER BY f.category ASC, f.name ASC")
    List<FoodItem> searchFoodItems(@Param("keyword") String keyword,
                                   @Param("status") String status);
}
