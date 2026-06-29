package com.group3.cinema.repository;

import com.group3.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComboRepository extends JpaRepository<Combo, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    // Cách viết logic thuần túy: Không dùng CASE WHEN, không dùng toán tử ba ngôi
    @Query("SELECT c FROM Combo c WHERE " +
            "(:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "( " +
            "  ((:status IS NULL OR :status = '') AND c.status = 'ACTIVE') OR " +
            "  (:status IS NOT NULL AND :status <> '' AND c.status = :status) " +
            ")")
    List<Combo> searchCombos(@Param("keyword") String keyword,
                             @Param("status") String status);
}