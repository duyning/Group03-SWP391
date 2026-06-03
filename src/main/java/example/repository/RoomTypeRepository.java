package example.repository;

import example.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    List<RoomType> findByActiveTrueOrderByNameAsc();

    List<RoomType> findAllByOrderByNameAsc();

    Optional<RoomType> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
