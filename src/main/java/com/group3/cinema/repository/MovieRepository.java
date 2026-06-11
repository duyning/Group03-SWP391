package com.group3.cinema.repository;

/*
 * Added on 2026-06-04: Repository queries for active movies and movie status.
 * Updated on 2026-06-04: Added UC-G03 active movie search by title keyword,
 * genre, and status only.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    /**
     * UC-G03 Search Movies:
     * - Search public/active movies only.
     * - keyword matches movie title partially.
     * - genre matches movie genre partially.
     * - status matches the movie status exactly.
     * - title and genre matching are case-insensitive.
     */
    @Query("""
            SELECT m
            FROM Movie m
            WHERE m.active = true
              AND (:keyword IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:genre IS NULL OR LOWER(m.genre) LIKE LOWER(CONCAT('%', :genre, '%')))
              AND (:status IS NULL OR m.status = :status)
            """)
    List<Movie> searchActiveMovies(@Param("keyword") String keyword,
                                   @Param("genre") String genre,
                                   @Param("status") Movie.MovieStatus status);

    @Query("""
            SELECT m
            FROM Movie m
            WHERE (:title IS NULL OR :title = '' OR LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%')))
              AND (:genre IS NULL OR :genre = '' OR LOWER(m.genre) LIKE LOWER(CONCAT('%', :genre, '%')))
              AND (:director IS NULL OR :director = '' OR LOWER(m.director) LIKE LOWER(CONCAT('%', :director, '%')))
              AND (:duration IS NULL OR m.duration = :duration)
              AND (:status IS NULL OR m.status = :status)
              AND (:releaseDate IS NULL OR m.releaseDate = :releaseDate)
            """)
    List<Movie> searchMovies(@Param("title") String title,
                             @Param("genre") String genre,
                             @Param("director") String director,
                             @Param("duration") Integer duration,
                             @Param("status") Movie.MovieStatus status,
                             @Param("releaseDate") LocalDate releaseDate);

    long countByStatus(Movie.MovieStatus status);

    @Query("""
            SELECT DISTINCT m.genre
            FROM Movie m
            WHERE m.active = true
              AND m.genre IS NOT NULL
              AND m.genre <> ''
            ORDER BY m.genre
            """)
    List<String> findDistinctActiveGenres();
}
