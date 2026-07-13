package com.group3.cinema.repository;

/*
 * Repository interface for database operations on WishlistItem.
 * Created by: Antigravity AI
 * Date: 2026-07-13
 */

import com.group3.cinema.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByAccountAccountID(int accountId);
    Optional<WishlistItem> findByAccountAccountIDAndMovieId(int accountId, int movieId);
    boolean existsByAccountAccountIDAndMovieId(int accountId, int movieId);
}
