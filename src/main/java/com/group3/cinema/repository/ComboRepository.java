package com.group3.cinema.repository;

import com.group3.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    @Query("SELECT c FROM Combo c WHERE " +
            "(:keyword IS NULL OR c.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR c.status = :status)")
    List<Combo> searchCombos(@Param("keyword") String keyword,
                             @Param("status") String status);
}