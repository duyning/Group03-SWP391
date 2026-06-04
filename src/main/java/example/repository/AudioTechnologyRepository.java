/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package example.repository;

import example.entity.AudioTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudioTechnologyRepository extends JpaRepository<AudioTechnology, Long> {

    List<AudioTechnology> findByActiveTrueOrderByNameAsc();

    List<AudioTechnology> findAllByOrderByNameAsc();

    Optional<AudioTechnology> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
