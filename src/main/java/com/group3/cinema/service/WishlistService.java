package com.group3.cinema.service;

/**
 * LUỒNG CHẠY SERVICE PHIM YÊU THÍCH (EXECUTION FLOW):
 * WishlistController -> WishlistService -> WishlistRepository -> Database
 * 
 * Các bước xử lý nghiệp vụ:
 * 1. Đảo trạng thái thả tim (toggleWishlist): Kiểm tra tồn tại trong CSDL -> Đã có (Delete bản ghi, trả false) : Chưa có (Save bản ghi mới, trả true).
 * 2. Lấy danh sách phim yêu thích (getWishlistMovies): Lấy danh sách theo accountId -> Lọc các phim active=true và deleted=false.
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

    /**
     * Bật/tắt thêm hoặc xóa một bộ phim khỏi danh sách phim yêu thích của tài khoản (Toggle Wishlist).
     * 
     * @param account Tài khoản khách hàng.
     * @param movieId ID bộ phim.
     * @return true nếu thêm vào yêu thích thành công, false nếu gỡ bỏ khỏi yêu thích.
     */
    @Transactional
    public boolean toggleWishlist(Account account, int movieId) {
        Optional<WishlistItem> itemOpt = wishlistRepository.findByAccountAccountIDAndMovieId(account.getAccountID(), movieId);
        if (itemOpt.isPresent()) {
            wishlistRepository.delete(itemOpt.get());
            return false;
        } else {
            Movie movie = movieRepository.findById(movieId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phim!"));
            WishlistItem item = new WishlistItem(account, movie);
            wishlistRepository.save(item);
            return true;
        }
    }

    /**
     * Kiểm tra xem một bộ phim có nằm trong danh sách yêu thích của khách hàng hay không.
     */
    public boolean isWishlisted(int accountId, int movieId) {
        Optional<Movie> movieOpt = movieRepository.findById(movieId);
        if (movieOpt.isEmpty() || !movieOpt.get().isActive() || movieOpt.get().isDeleted()) {
            return false;
        }
        return wishlistRepository.existsByAccountAccountIDAndMovieId(accountId, movieId);
    }

    /**
     * Lấy danh sách tất cả các bộ phim yêu thích còn hoạt động của một khách hàng.
     */
    public List<Movie> getWishlistMovies(int accountId) {
        return wishlistRepository.findByAccountAccountID(accountId).stream()
                .map(WishlistItem::getMovie)
                .filter(movie -> movie != null && movie.isActive() && !movie.isDeleted())
                .toList();
    }
}

