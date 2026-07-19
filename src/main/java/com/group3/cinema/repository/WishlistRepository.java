package com.group3.cinema.repository;

/*
 * Repository interface for database operations on WishlistItem.
 * Created by: Antigravity AI
 * Date: 2026-07-13
 */

import com.group3.cinema.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    @Query("SELECT w FROM WishlistItem w WHERE w.account.accountID = :accountId")
    List<WishlistItem> findByAccountAccountID(@Param("accountId") int accountId);

    @Query("SELECT w FROM WishlistItem w WHERE w.account.accountID = :accountId AND w.movie.id = :movieId")
    Optional<WishlistItem> findByAccountAccountIDAndMovieId(@Param("accountId") int accountId, @Param("movieId") int movieId);

    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WishlistItem w WHERE w.account.accountID = :accountId AND w.movie.id = :movieId")
    boolean existsByAccountAccountIDAndMovieId(@Param("accountId") int accountId, @Param("movieId") int movieId);
}
