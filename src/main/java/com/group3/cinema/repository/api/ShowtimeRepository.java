package com.group3.cinema.repository.api;

/**
 * Dá»± Ã¡n: Cinema 2026 â€” SWP391 Group 03
 * File: ShowtimeRepository.java
 * Chá»©c nÄƒng: Lá»›p giao diá»‡n (Interface) Repository quáº£n lÃ½ viá»‡c truy xuáº¥t dá»¯ liá»‡u cá»§a báº£ng "showtimes"
 *            thÃ´ng qua Spring Data JPA. Há»— trá»£ thao tÃ¡c CRUD cÆ¡ báº£n vÃ  Ä‘á»‹nh nghÄ©a truy váº¥n tÃ¬m kiáº¿m
 *            lá»‹ch chiáº¿u nÃ¢ng cao dá»±a trÃªn nhiá»u tiÃªu chÃ­ (ID phim, loáº¡i ngÃ y, khoáº£ng thá»i gian).
 * NgÆ°á»i viáº¿t: TrienLX - HE182285
 * NgÃ y táº¡o: 2026-06-04
 */

import com.group3.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// ÄÃ¡nh dáº¥u Ä‘Ã¢y lÃ  má»™t repository xá»­ lÃ½ dá»¯ liá»‡u cho Ä‘á»‘i tÆ°á»£ng Showtime
@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    // Äá»‹nh nghÄ©a truy váº¥n JPQL Ä‘á»™ng giÃºp lá»c tÃ¬m kiáº¿m lá»‹ch chiáº¿u theo nhiá»u tiÃªu chÃ­ tÃ¹y chá»n cÃ¹ng lÃºc
    @Query("SELECT s FROM Showtime s WHERE " +
           "(:movieId IS NULL OR s.movie.id = :movieId) AND " +
           "(:dayType IS NULL OR :dayType = '' OR s.dayType = :dayType) AND " +
           "(:startDate IS NULL OR s.showDate >= :startDate) AND " +
           "(:endDate IS NULL OR s.showDate <= :endDate) " +
           "ORDER BY s.showDate ASC, s.showTime ASC")
    List<Showtime> searchShowtimes(
            @Param("movieId") Integer movieId,
            @Param("dayType") String dayType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Truy váº¥n tá»± Ä‘á»™ng cá»§a JPA Ä‘áº¿m sá»‘ lÆ°á»£ng lá»‹ch chiáº¿u theo loáº¡i ngÃ y cá»¥ thá»ƒ
    long countByDayType(String dayType);

    boolean existsByRoomIgnoreCaseAndShowDateGreaterThanEqual(String room, LocalDate showDate);

    List<Showtime> findByRoomIgnoreCaseAndShowDate(String room, LocalDate showDate);

    long countByRoomIgnoreCase(String room);
}
