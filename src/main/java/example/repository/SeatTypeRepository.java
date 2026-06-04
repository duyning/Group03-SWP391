package example.repository;

import example.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {

    List<SeatType> findByActiveTrueOrderByIdAsc();

    List<SeatType> findAllByOrderByIdAsc();

    Optional<SeatType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
