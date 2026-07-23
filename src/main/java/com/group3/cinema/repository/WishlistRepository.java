/**
 * Interface Repository thao tác dữ liệu Danh sách Phim Yêu thích của khách hàng (`wishlist`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `WishlistService` và `CustomerController` để xử lý thêm/xóa phim khỏi danh sách yêu thích và kiểm tra trạng thái tim đỏ icon phim.
 * 
 * Khởi tạo: 13/07/2026
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    /** Lấy toàn bộ danh sách mục phim yêu thích của một tài khoản. */
    @Query("SELECT w FROM WishlistItem w WHERE w.account.accountID = :accountId")
    List<WishlistItem> findByAccountAccountID(@Param("accountId") int accountId);

    /** Tìm một mục phim yêu thích cụ thể theo ID tài khoản và ID phim. */
    @Query("SELECT w FROM WishlistItem w WHERE w.account.accountID = :accountId AND w.movie.id = :movieId")
    Optional<WishlistItem> findByAccountAccountIDAndMovieId(@Param("accountId") int accountId, @Param("movieId") int movieId);

    /** Kiểm tra xem bộ phim đã nằm trong danh sách yêu thích của tài khoản chưa (để render icon tim màu đỏ/xám). */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WishlistItem w WHERE w.account.accountID = :accountId AND w.movie.id = :movieId")
    boolean existsByAccountAccountIDAndMovieId(@Param("accountId") int accountId, @Param("movieId") int movieId);
}

