/**
 * Interface Repository thao tác dữ liệu phim chiếu rạp (`movie`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `MovieService`, `PublicContentInitializer`, `CustomerBookingService`, `CatalogInitializer`.
 * - Hỗ trợ các chức năng: Tìm kiếm phim hiển thị khách hàng (`searchActiveMovies`), tìm kiếm đa điều kiện trang Admin (`searchMovies`),
 *   tự động cập nhật phim sắp chiếu thành đang chiếu khi đến ngày phát hành (`autoUpdateUpcomingToNowShowing`),
 *   tự động dừng chiếu các phim hết suất chiếu (`autoDeactivateExpiredMovies`),
 *   kiểm tra trùng lặp tiêu đề, poster, trailer phim (`existsDuplicateTitle`, `existsDuplicatePoster`, `existsDuplicateTrailer`).
 * 
 * Khởi tạo bởi: HuyPB - HE191335 (04/06/2026)
 * Cập nhật bởi: TrienLX (23/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {

    /**
     * Lấy tất cả danh sách phim chưa bị xóa mềm (`deleted = false`).
     */
    @Override
    @Query("SELECT m FROM Movie m WHERE m.deleted = false")
    List<Movie> findAll();

    /**
     * Lấy danh sách tất cả phim đang hiển thị công khai cho khách hàng (`active = true`).
     */
    List<Movie> findByActiveTrue();

    /**
     * Lấy danh sách phim công khai theo trạng thái chiếu (NOW_SHOWING, COMING_SOON, SPECIAL_SCREENING).
     */
    List<Movie> findByStatusAndActiveTrue(Movie.MovieStatus status);

    /**
     * Lấy top 5 phim mới nhất đang hoạt động hiển thị trên Trang chủ.
     */
    List<Movie> findTop5ByActiveTrueOrderByIdDesc();

    /**
     * Tìm chi tiết một bộ phim đang hoạt động phục vụ trang chi tiết phim (`movie-detail.html`).
     */
    Optional<Movie> findByIdAndActiveTrue(int id);

    /**
     * Tìm kiếm danh sách phim đang hiển thị cho phía khách hàng (UC-G03):
     * Hỗ trợ tìm theo từ khóa tên phim (`keyword`), thể loại (`genre`), và trạng thái (`status`).
     */
    @Query("""
            SELECT m
            FROM Movie m
            WHERE m.active = true AND m.deleted = false
              AND (m.status <> com.group3.cinema.entity.Movie$MovieStatus.STOPPED OR :status = com.group3.cinema.entity.Movie$MovieStatus.STOPPED)
              AND (:keyword IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:genre IS NULL OR LOWER(m.genre) LIKE LOWER(CONCAT('%', :genre, '%')))
              AND (:status IS NULL OR m.status = :status)
            """)
    List<Movie> searchActiveMovies(@Param("keyword") String keyword,
                                   @Param("genre") String genre,
                                   @Param("status") Movie.MovieStatus status);

    /**
     * Tìm kiếm danh sách phim đa chỉ tiêu phục vụ trang quản lý danh mục phim của Admin/Manager.
     */
    @Query("""
             SELECT m
             FROM Movie m
             WHERE m.deleted = false
               AND (:title IS NULL OR :title = '' OR LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%')))
               AND (:genre IS NULL OR :genre = '' OR LOWER(m.genre) LIKE LOWER(CONCAT('%', :genre, '%')))
               AND (:director IS NULL OR :director = '' OR LOWER(m.director) LIKE LOWER(CONCAT('%', :director, '%')))
               AND (:duration IS NULL OR m.duration = :duration)
               AND (:status IS NULL OR m.status = :status)
               AND (:releaseDate IS NULL OR m.releaseDate = :releaseDate)
               AND (:active IS NULL OR m.active = :active)
             """)
    List<Movie> searchMovies(@Param("title") String title,
                             @Param("genre") String genre,
                             @Param("director") String director,
                             @Param("duration") Integer duration,
                             @Param("status") Movie.MovieStatus status,
                             @Param("releaseDate") LocalDate releaseDate,
                             @Param("active") Boolean active);

    /** Đếm tổng số phim chưa bị xóa mềm. */
    @Override
    @Query("SELECT COUNT(m) FROM Movie m WHERE m.deleted = false")
    long count();

    /** Đếm số lượng phim theo trạng thái trình chiếu. */
    @Query("SELECT COUNT(m) FROM Movie m WHERE m.status = :status AND m.deleted = false")
    long countByStatus(@Param("status") Movie.MovieStatus status);

    /** Đếm số lượng phim bị ẩn (`active = false`). */
    @Query("SELECT COUNT(m) FROM Movie m WHERE m.active = false AND m.deleted = false")
    long countByActiveFalse();

    /**
     * Tự động chuyển trạng thái phim từ COMING_SOON thành NOW_SHOWING khi ngày phát hành nhỏ hơn hoặc bằng ngày hiện tại.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Movie m SET m.status = :nowShowing WHERE m.active = true AND m.status = :comingSoon AND m.releaseDate <= :today")
    int autoUpdateUpcomingToNowShowing(@Param("today") LocalDate today,
                                       @Param("nowShowing") Movie.MovieStatus nowShowing,
                                       @Param("comingSoon") Movie.MovieStatus comingSoon);

    /**
     * Tự động đặt trạng thái phim thành STOPPED và ẩn (`active = false`) khi tất cả các suất chiếu của phim đã kết thúc (không còn suất chiếu từ ngày hôm nay trở đi).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Movie m SET m.status = :stoppedStatus, m.active = false WHERE m.active = true AND m.status <> :stoppedStatus AND m.releaseDate <= :today AND NOT EXISTS (SELECT 1 FROM Showtime s WHERE s.movie.id = m.id AND s.showDate >= :today)")
    int autoDeactivateExpiredMovies(@Param("today") LocalDate today, @Param("stoppedStatus") Movie.MovieStatus stoppedStatus);

    /**
     * Ẩn tất cả các phim đang ở trạng thái STOPPED.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Movie m SET m.active = false WHERE m.status = :stoppedStatus AND m.active = true")
    int deactivateStoppedMovies(@Param("stoppedStatus") Movie.MovieStatus stoppedStatus);

    /**
     * Trả về danh sách tên các thể loại phim riêng biệt đang hoạt động để đổ vào dropdown bộ lọc.
     */
    @Query("""
            SELECT DISTINCT m.genre
            FROM Movie m
            WHERE m.active = true
              AND m.genre IS NOT NULL
              AND m.genre <> ''
            ORDER BY m.genre
            """)
    List<String> findDistinctActiveGenres();

    /** Kiểm tra trùng tên phim giữa các bộ phim chưa bị xóa mềm (bao gồm cả phim đang ẩn active = false). */
    @Query("SELECT COUNT(m) > 0 FROM Movie m WHERE LOWER(m.title) = LOWER(:title) AND m.id <> :id AND m.deleted = false")
    boolean existsDuplicateTitle(@Param("title") String title, @Param("id") int id);

    /** Kiểm tra trùng URL Poster phim (bao gồm cả phim đang ẩn active = false, không tính phim bị xóa mềm). */
    @Query("SELECT COUNT(m) > 0 FROM Movie m WHERE m.posterUrl = :posterUrl AND m.posterUrl IS NOT NULL AND m.posterUrl <> '' AND m.id <> :id AND m.deleted = false")
    boolean existsDuplicatePoster(@Param("posterUrl") String posterUrl, @Param("id") int id);

    /** Kiểm tra trùng URL Trailer phim (bao gồm cả phim đang ẩn active = false, không tính phim bị xóa mềm). */
    @Query("SELECT COUNT(m) > 0 FROM Movie m WHERE m.trailerUrl = :trailerUrl AND m.trailerUrl IS NOT NULL AND m.trailerUrl <> '' AND m.id <> :id AND m.deleted = false")
    boolean existsDuplicateTrailer(@Param("trailerUrl") String trailerUrl, @Param("id") int id);

    /** Tìm phim đã bị xóa mềm theo tiêu đề (dùng cho khôi phục). */
    @Query("SELECT m FROM Movie m WHERE LOWER(m.title) = LOWER(:title) AND m.deleted = true")
    Optional<Movie> findSoftDeletedByTitle(@Param("title") String title);
}

