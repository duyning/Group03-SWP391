package com.group3.cinema.service;

/*
 * Service class implementing business logic for movie Wishlist.
 * Created by: Antigravity AI
 * Date: 2026-07-13
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.WishlistItem;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final MovieRepository movieRepository;

    public WishlistService(WishlistRepository wishlistRepository, MovieRepository movieRepository) {
        this.wishlistRepository = wishlistRepository;
        this.movieRepository = movieRepository;
    }

    @Transactional
    public boolean toggleWishlist(Account account, int movieId) {
        Optional<WishlistItem> itemOpt = wishlistRepository.findByAccountAccountIDAndMovieId(account.getAccountID(), movieId);
        if (itemOpt.isPresent()) {
            wishlistRepository.delete(itemOpt.get());
            return false; // Removed
        } else {
            Movie movie = movieRepository.findById(movieId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phim!"));
            WishlistItem item = new WishlistItem(account, movie);
            wishlistRepository.save(item);
            return true; // Added
        }
    }

    public boolean isWishlisted(int accountId, int movieId) {
        return wishlistRepository.existsByAccountAccountIDAndMovieId(accountId, movieId);
    }

    public List<Movie> getWishlistMovies(int accountId) {
        return wishlistRepository.findByAccountAccountID(accountId).stream()
                .map(WishlistItem::getMovie)
                .toList();
    }
}
