package com.group3.cinema.repository.api;

/*
 * Repository quản lý truy xuất dữ liệu lịch chiếu.
 * Created/updated by: TrienLX - HE182285, NinhDD - HE186113
 */

import com.group3.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    @Override
    @Query("SELECT s FROM Showtime s WHERE s.active = true")
    List<Showtime> findAll();

    @Override
    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.active = true")
    long count();

    @Query("""
            SELECT s
            FROM Showtime s
            WHERE s.active = true
              AND (:movieId IS NULL OR s.movie.id = :movieId)
              AND (:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType)
              AND (:startDate IS NULL OR s.showDate >= :startDate)
              AND (:endDate IS NULL OR s.showDate <= :endDate)
            ORDER BY s.showDate ASC, s.showTime ASC
            """)
    List<Showtime> searchShowtimes(@Param("movieId") Integer movieId,
                                   @Param("dayType") String dayType,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT s
            FROM Showtime s
            WHERE s.active = true
              AND s.movie.active = true
              AND s.movie.status <> com.group3.cinema.entity.Movie$MovieStatus.STOPPED
              AND (:movieId IS NULL OR s.movie.id = :movieId)
              AND (:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType)
              AND (:startDate IS NULL OR s.showDate >= :startDate)
              AND (:endDate IS NULL OR s.showDate <= :endDate)
            ORDER BY s.showDate ASC, s.showTime ASC
            """)
    List<Showtime> searchShowtimesForCustomer(@Param("movieId") Integer movieId,
                                             @Param("dayType") String dayType,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.dayType = :dayType AND s.active = true")
    long countByDayType(@Param("dayType") String dayType);

    boolean existsByRoomIgnoreCaseAndShowDateGreaterThanEqual(String room, LocalDate showDate);

    @Query("""
            SELECT s
            FROM Showtime s
            WHERE LOWER(s.room) = LOWER(:room)
              AND s.showDate = :showDate
              AND s.active = true
            """)
    List<Showtime> findByRoomIgnoreCaseAndShowDate(@Param("room") String room,
                                                   @Param("showDate") LocalDate showDate);

    long countByRoomIgnoreCase(String room);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.showDate = :showDate AND s.active = true")
    long countByShowDate(@Param("showDate") LocalDate showDate);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.showDate > :showDate AND s.active = true")
    long countByShowDateGreaterThan(@Param("showDate") LocalDate showDate);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.showDate < :showDate AND s.active = true")
    long countByShowDateLessThan(@Param("showDate") LocalDate showDate);

    @Query("""
            SELECT s
            FROM Showtime s
            WHERE s.movie.id = :movieId
              AND s.showDate = :showDate
              AND s.active = true
            """)
    List<Showtime> findByMovieIdAndShowDate(@Param("movieId") int movieId,
                                            @Param("showDate") LocalDate showDate);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.movie.id = :movieId AND s.active = true")
    long countAllShowtimesByMovieId(@Param("movieId") int movieId);

    @Query("SELECT COUNT(s) FROM Showtime s WHERE s.movie.id = :movieId AND s.showDate >= :today AND s.active = true")
    long countFutureShowtimesByMovieId(@Param("movieId") int movieId, @Param("today") LocalDate today);
}
