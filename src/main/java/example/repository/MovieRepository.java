package example.repository;

/*
 * Added on 2026-06-04: Repository queries for active movies and movie status.
 * Created by: HuyPB - HE191335
 */

import example.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {

    /**
     * Load all movies that are allowed to appear on public customer screens.
     * This is the base query for banner, list, and search flows.
     */
    List<Movie> findByActiveTrue();

    /**
     * Load public movies by business status.
     * Used to split movies into "now showing", "coming soon", and
     * "special screening" sections.
     */
    List<Movie> findByStatusAndActiveTrue(Movie.MovieStatus status);

    /**
     * Load the latest 5 public movies by id.
     * Kept for homepage/latest movie sections if the UI needs newest records.
     */
    List<Movie> findTop5ByActiveTrueOrderByIdDesc();

    /**
     * Load one public movie for the detail page.
     * Hidden/inactive movies are not returned, so customers cannot view them
     * directly by typing the id in the URL.
     */
    Optional<Movie> findByIdAndActiveTrue(int id);
}
