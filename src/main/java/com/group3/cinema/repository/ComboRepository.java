package com.group3.cinema.repository;

import com.group3.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    List<Combo> findByStatusInOrderByNameAsc(List<String> statuses);

    // 1. Kiểm tra xem tên combo đã tồn tại chưa (phục vụ lúc tạo mới)
    boolean existsByName(String name);

    // 2. Kiểm tra xem tên combo đã tồn tại ở một bản ghi khác chưa (phục vụ lúc edit)
    boolean existsByNameAndIdNot(String name, Long id);

    @Query("SELECT DISTINCT c FROM Combo c " +
            "LEFT JOIN FETCH c.items i " +
            "LEFT JOIN FETCH i.foodItem " +
            "WHERE c.id = :id")
    Optional<Combo> findWithItemsById(@Param("id") Long id);

    @Query("SELECT c FROM Combo c WHERE " +
            "(:keyword IS NULL OR c.name LIKE %:keyword%) AND " +
            "(:status IS NULL OR :status = '' OR c.status = :status)")
    List<Combo> searchCombos(@Param("keyword") String keyword,
                             @Param("status") String status);
}
