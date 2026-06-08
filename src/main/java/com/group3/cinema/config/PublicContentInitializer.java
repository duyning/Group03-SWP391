package com.group3.cinema.config;

/*
 * Created on 2026-06-09: Seed customer-facing demo movies and news when tables are empty.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Post;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.PostRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class PublicContentInitializer {

    private final MovieRepository movieRepository;
    private final PostRepository postRepository;

    public PublicContentInitializer(MovieRepository movieRepository, PostRepository postRepository) {
        this.movieRepository = movieRepository;
        this.postRepository = postRepository;
    }

    @PostConstruct
    @Transactional
    public void seedPublicContent() {
        seedMovies();
        seedPosts();
    }

    private void seedMovies() {
        if (movieRepository.count() > 0) {
            return;
        }

        movieRepository.saveAll(List.of(
                movie("Doraemon: Nobita Và Bản Giao Hưởng Địa Cầu", "Hoạt hình, Gia đình", 115,
                        LocalDate.of(2026, 6, 14), Movie.MovieStatus.NOW_SHOWING,
                        "https://images.unsplash.com/photo-1572039558269-46922af9c109?auto=format&fit=crop&w=700&q=80",
                        "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=1600&q=80",
                        "Một chuyến phiêu lưu âm nhạc đầy cảm xúc, nơi Nobita và những người bạn bảo vệ hành tinh bằng giai điệu của tình bạn.",
                        "Kazuaki Imai", "Doraemon, Nobita, Shizuka, Jaian, Suneo", "Lồng tiếng Việt", "P"),
                movie("Lật Mặt: Vòng Xoáy Ký Ức", "Hành động, Tâm lý", 128,
                        LocalDate.of(2026, 6, 20), Movie.MovieStatus.NOW_SHOWING,
                        "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?auto=format&fit=crop&w=700&q=80",
                        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1600&q=80",
                        "Một vụ mất tích kéo theo chuỗi bí mật gia đình, những màn rượt đuổi nghẹt thở và lựa chọn sinh tử.",
                        "Lý Hải", "Quốc Cường, Diệp Bảo Ngọc, Thanh Thức", "Tiếng Việt", "T13"),
                movie("Inside Out 2", "Hoạt hình, Phiêu lưu", 100,
                        LocalDate.of(2026, 6, 28), Movie.MovieStatus.COMING_SOON,
                        "https://images.unsplash.com/photo-1502136969935-8d8eef54d77b?auto=format&fit=crop&w=700&q=80",
                        "https://images.unsplash.com/photo-1535223289827-42f1e9919769?auto=format&fit=crop&w=1600&q=80",
                        "Những cảm xúc mới xuất hiện, khiến thế giới nội tâm tuổi trưởng thành trở nên rực rỡ và nhiều bất ngờ hơn.",
                        "Kelsey Mann", "Amy Poehler, Maya Hawke, Kensington Tallman", "Phụ đề Việt", "P"),
                movie("Đêm Gala Điện Ảnh Việt", "Sự kiện, Âm nhạc", 150,
                        LocalDate.of(2026, 7, 5), Movie.MovieStatus.SPECIAL_SCREENING,
                        "https://images.unsplash.com/photo-1503095396549-807759245b35?auto=format&fit=crop&w=700&q=80",
                        "https://images.unsplash.com/photo-1507924538820-ede94a04019d?auto=format&fit=crop&w=1600&q=80",
                        "Suất chiếu đặc biệt kết hợp giao lưu nghệ sĩ, thảm đỏ mini và phần trình diễn âm nhạc sau phim.",
                        "Beta Cinemas", "Khách mời đặc biệt", "Tiếng Việt", "T13"),
                movie("Vùng Đất Im Lặng: Ngày Một", "Kinh dị, Giật gân", 99,
                        LocalDate.of(2026, 7, 12), Movie.MovieStatus.COMING_SOON,
                        "https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?auto=format&fit=crop&w=700&q=80",
                        "https://images.unsplash.com/photo-1524985069026-dd778a71c7b4?auto=format&fit=crop&w=1600&q=80",
                        "Khi thành phố chìm trong tĩnh lặng, từng tiếng động nhỏ đều có thể đổi lấy mạng sống.",
                        "Michael Sarnoski", "Lupita Nyong'o, Joseph Quinn", "Phụ đề Việt", "T16")
        ));
    }

    private Movie movie(String title, String genre, Integer duration, LocalDate releaseDate,
                        Movie.MovieStatus status, String posterUrl, String bannerUrl,
                        String description, String director, String cast,
                        String language, String ageRating) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setDuration(duration);
        movie.setReleaseDate(releaseDate);
        movie.setStatus(status);
        movie.setPosterUrl(posterUrl);
        movie.setBannerUrl(bannerUrl);
        movie.setDescription(description);
        movie.setDirector(director);
        movie.setCast(cast);
        movie.setLanguage(language);
        movie.setAgeRating(ageRating);
        movie.setActive(true);
        return movie;
    }

    private void seedPosts() {
        if (postRepository.count() > 0) {
            return;
        }

        postRepository.saveAll(List.of(
                post("Beta Cinemas ra mắt tuần lễ phim hè 2026",
                        "Tin rạp", "Beta Cinemas",
                        "Hàng loạt bom tấn, suất chiếu sớm và ưu đãi combo được triển khai trong tuần lễ phim hè.",
                        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1000&q=80",
                        "<p>Tuần lễ phim hè 2026 mang đến lịch chiếu dày đặc cho các nhóm khán giả trẻ, gia đình và người yêu điện ảnh.</p><p>Khách hàng có thể theo dõi lịch chiếu mới nhất trên website, đặt vé trước và nhận nhiều ưu đãi combo bắp nước theo từng khung giờ.</p>",
                        "tin-rap, phim-he, beta-cinemas", LocalDateTime.now().minusDays(1)),
                post("5 mẹo chọn ghế đẹp khi xem phim tại rạp",
                        "Cẩm nang", "Beta Cinemas",
                        "Chọn vị trí ghế phù hợp giúp trải nghiệm hình ảnh, âm thanh và cảm xúc phim trọn vẹn hơn.",
                        "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=1000&q=80",
                        "<p>Với phòng chiếu tiêu chuẩn, hàng giữa thường cho góc nhìn cân bằng nhất. Với phim hành động hoặc IMAX, khán giả nên ưu tiên khu vực trung tâm để cảm nhận âm thanh vòm rõ hơn.</p><p>Nếu đi cùng gia đình, hãy đặt vé sớm để chọn được cụm ghế liền nhau và tránh các vị trí quá gần màn hình.</p>",
                        "chon-ghe, kinh-nghiem, dat-ve", LocalDateTime.now().minusDays(3)),
                post("Combo mới cho nhóm bạn cuối tuần",
                        "Khuyến mãi", "Beta Cinemas",
                        "Các combo bắp nước mới được thiết kế cho nhóm 2-4 người với mức giá tiết kiệm hơn.",
                        "https://images.unsplash.com/photo-1542204625-ca960ca44635?auto=format&fit=crop&w=1000&q=80",
                        "<p>Beta Cinemas bổ sung nhiều lựa chọn combo mới, phù hợp cho nhóm bạn và gia đình trong các suất chiếu cuối tuần.</p><p>Khách hàng có thể mua combo cùng lúc đặt vé để rút ngắn thời gian chờ tại quầy.</p>",
                        "combo, khuyen-mai, cuoi-tuan", LocalDateTime.now().minusDays(5)),
                post("Lịch chiếu sớm các phim được mong chờ",
                        "Sự kiện", "Beta Cinemas",
                        "Một số phim hot sẽ có suất chiếu sớm giới hạn trước ngày khởi chiếu chính thức.",
                        "https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?auto=format&fit=crop&w=1000&q=80",
                        "<p>Các suất chiếu sớm thường có số lượng vé giới hạn và mở bán theo từng cụm rạp.</p><p>Khán giả nên theo dõi website thường xuyên để không bỏ lỡ lịch mở bán và ưu đãi đi kèm.</p>",
                        "suat-chieu-som, lich-chieu, su-kien", LocalDateTime.now().minusDays(7))
        ));
    }

    private Post post(String title, String category, String author, String summary,
                      String thumbnail, String content, String tags, LocalDateTime publishedAt) {
        Post post = new Post();
        post.setTitle(title);
        post.setCategory(category);
        post.setAuthor(author);
        post.setSummary(summary);
        post.setThumbnail(thumbnail);
        post.setContent(content);
        post.setTags(tags);
        post.setStatus("PUBLISHED");
        post.setPublishedAt(publishedAt);
        return post;
    }
}
