/**
 * Lớp khởi tạo dữ liệu công khai trên trang chủ (PublicContentInitializer).
 * 
 * Tự động chạy khi ứng dụng khởi động (`@PostConstruct`) nếu thuộc tính `app.seed.public-content` được bật (`true`):
 * 1. Khởi tạo danh sách Phim mẫu (Beta Movies) nếu CSDL chưa có hoặc cập nhật thông tin phim.
 * 2. Khởi tạo danh sách Bài viết / Tin tức rạp (`Post`).
 * 3. Khởi tạo các Chiến dịch Khuyến mãi (`Promotion`: Happy Monday, Member Day, Student Screen, Bank Weekend).
 * 
 * Phụ thuộc vào `roomUnicodeMigration` (thông qua `@DependsOn`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (09/06/2026)
 */
package com.group3.cinema.config;

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Post;
import com.group3.cinema.entity.Promotion;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.PostRepository;
import com.group3.cinema.repository.PromotionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@DependsOn("roomUnicodeMigration")
public class PublicContentInitializer {

    private final MovieRepository movieRepository;
    private final PostRepository postRepository;
    private final PromotionRepository promotionRepository;
    private final boolean seedPublicContentEnabled;

    /**
     * Constructor tiêm phụ thuộc các Repositories và cấu hình `app.seed.public-content`.
     * 
     * @param movieRepository Repository quản lý Phim.
     * @param postRepository Repository quản lý Bài viết/Tin tức.
     * @param promotionRepository Repository quản lý Khuyến mãi.
     * @param seedPublicContentEnabled Cờ bật/tắt seed dữ liệu công khai từ file cấu hình (default: false).
     */
    public PublicContentInitializer(MovieRepository movieRepository, PostRepository postRepository,
                                    PromotionRepository promotionRepository,
                                    @Value("${app.seed.public-content:false}") boolean seedPublicContentEnabled) {
        this.movieRepository = movieRepository;
        this.postRepository = postRepository;
        this.promotionRepository = promotionRepository;
        this.seedPublicContentEnabled = seedPublicContentEnabled;
    }

    /**
     * Phương thức chính chạy khi khởi động ứng dụng.
     * Kiểm tra cờ `seedPublicContentEnabled`, nếu `true` sẽ gọi lần lượt:
     * - `seedMovies()`
     * - `seedPosts()`
     * - `seedPromotions()`
     */
    @PostConstruct
    @Transactional
    public void seedPublicContent() {
        if (!seedPublicContentEnabled) {
            return;
        }
        seedMovies();
        seedPosts();
        seedPromotions();
    }

    /**
     * Khởi tạo danh sách phim mẫu từ rạp Beta (13 bộ phim hot).
     * Gọi `movieRepository.findAll()`, nếu đã có phim sẽ cập nhật thông tin chuẩn qua `copyMovieData()`,
     * nếu chưa có sẽ tạo mới và lưu hàng loạt bằng `movieRepository.saveAll()`.
     */
    private void seedMovies() {
        if (movieRepository.count() > 0) {
            return;
        }

        List<Movie> betaMovies = List.of(
                betaMovie("Bầy Xác Sống", "Kinh dị, Hồi Hộp", 122, LocalDate.of(2026, 6, 12), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f09%2f400x633%2D1%2D143138%2D090626%2D39.jpg", "https://www.youtube.com/watch?v=WFu0aDRxj0U",
                        "Giáo sư Se Jeong tham dự một hội nghị công nghệ sinh học, nhưng lại chứng kiến nó biến thành thảm họa khi một loại virus đột biến nhanh chóng được giải phóng. Khi dịch bệnh lan rộng và những người nhiễm bệnh bắt đầu biến đổi, chính quyền đã phong tỏa toàn bộ cơ sở.",
                        "Yeon Sang-ho", "Jun Ji-hyun, Koo Kyo-hwan, Ji Chang-wook", "Tiếng Hàn", "C-16"),
                betaMovie("Lầu Chú Hỏa", "Kinh dị", 94, LocalDate.of(2026, 6, 12), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f05%2f18%2f400wx633h%2D142000%2D180526%2D24.jpg", "https://www.youtube.com/watch?v=R2yZRReGSVM",
                        "Lầu Chú Hỏa theo chân một nhóm streamer trẻ đột nhập vào căn biệt thự bỏ hoang gắn với truyền thuyết con ma nhà họ Hứa. Sự xâm phạm vào vùng đất cấm đã vô tình đánh thức ác linh bị trấn yểm suốt hơn 100 năm.",
                        "Hùng Trần", "Trần Kỳ Anh, Nguyễn Minh Thời, Ngọc Chi Bảo", "Tiếng Việt", "C-18"),
                betaMovie("Mesdames Thanh Sắc", "Tội Phạm, Tình cảm", 125, LocalDate.of(2026, 6, 19), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f05%2f27%2fmain%2Dmts%2D9x16%2D104327%2D270526%2D84.jpg", "https://www.youtube.com/watch?v=2kOnAEy9AXo",
                        "Madames Thanh Sắc xoay quanh cuộc đời của đại mỹ nhân Cầm Thanh và Madame Sắc, bà chủ vũ trường Kim Đô giàu có. Hai người phụ nữ bắt đầu cuộc giằng co căng thẳng dẫn đến những sự kiện gây rúng động.",
                        "Thắng Vũ", "Thanh Hằng, Hồng Ánh", "Tiếng Việt", "C-18"),
                betaMovie("Minions & Quái Vật", "Hoạt hình", 90, LocalDate.of(2026, 7, 1), Movie.MovieStatus.COMING_SOON,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f11%2f010726%2Dminions%2Dand%2Dmonsters%2D165423%2D110626%2D82.jpg", "https://www.youtube.com/watch?v=V-O-uBaHk3c",
                        "Minions & Quái Vật là câu chuyện vừa náo loạn, vừa ngớ ngẩn về cách Minions chinh phục Hollywood, vô tình thả quái vật ra khắp thế giới và phải cùng nhau cứu lấy hành tinh.",
                        "Pierre Coffin", "Bobby Moynihan, Zoey Deutch, Allison Janney", "Tiếng Việt", "P"),
                betaMovie("Phim Điện Ảnh Doraemon: Nobita Và Lâu Đài Dưới Đáy Biển - Phiên Bản Mới", "Hoạt hình", 101, LocalDate.of(2026, 5, 22), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f04%2f13%2fdora26%2Dhon%2Dposter%2Dcopy%2D3%2D100%2D115842%2D130426%2D82.jpg", "https://www.youtube.com/watch?v=OFNUhDb-FDo",
                        "Trong kỳ nghỉ hè, Nobita và các bạn cắm trại dưới đáy biển, gặp người đáy biển từ Liên bang Mu và bắt đầu cuộc phiêu lưu để ngăn Lâu đài Đá Quỷ cứu Trái đất.",
                        "Tetsuo Yajima", "Wasabi Mizuta, Megumi Oohara, Yumi Kakazu, Subaru Kimura, Tomokazu Seki", "Tiếng Việt", "P"),
                betaMovie("Ám Ảnh", "Giật gân, Kinh dị", 109, LocalDate.of(2026, 6, 19), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f15%2fobs%2Dpayoff%2D400x633%2D170323%2D150626%2D19.jpg", "https://www.youtube.com/watch?v=gMC8kkwbIQQ",
                        "Bear bẻ gãy món đồ chơi bí ẩn Liễu Ước Nguyện để đổi lấy tình yêu. Điều ước thành hiện thực, nhưng hạnh phúc nhanh chóng biến thành cơn ác mộng với cái giá kinh hoàng.",
                        "Curry Barker", "Michael Johnston, Inde Navarrette, Cooper Tomlinson, Megan Lawless, Andy Richter", "Tiếng Anh", "C-18"),
                betaMovie("Câu Chuyện Đồ Chơi 5", "Hoạt hình, Phiêu lưu", 102, LocalDate.of(2026, 6, 19), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f11%2fanh%2Dchup%2Dman%2Dhinh%2D2026%2D06%2D11%2D151148%2D151225%2D110626%2D59.png", "https://www.youtube.com/watch?v=yVZ0JQvXmKk",
                        "Buzz, Woody, Jessie cùng cả nhóm trở lại trong cuộc đối đầu giữa đồ chơi và công nghệ, khi một mối đe dọa mới xuất hiện với niềm vui vui chơi.",
                        "Kenna Harris, Andrew Stanton", "Keanu Reeves, Tom Hanks, Annie Potts", "Tiếng Việt", "P"),
                betaMovie("Linh Miêu Báo Thù", "Kinh dị", 87, LocalDate.of(2026, 6, 26), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f11%2f260626%2Ddevil%2Dblack%2Dcat%2D164459%2D110626%2D22.jpg", "https://www.youtube.com/watch?v=bxJ-NH8RlNQ",
                        "Sarah đến một ngôi làng hẻo lánh bị đồn nguyền rủa bởi quỷ mèo đen. Khi những cái chết bí ẩn xảy ra, cô khám phá bí mật đen tối phía sau truyền thuyết.",
                        "", "Anna Glucks, Indy Intad Leowrakwong, Wayne Falconer", "Tiếng Thái", "C-16"),
                betaMovie("Ma Lu", "Kinh dị", 98, LocalDate.of(2026, 6, 19), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f11%2fone%2Dsheet%2D70x100%2D150842%2D110626%2D11.png", "https://www.youtube.com/watch?v=GK6QliPI4gI",
                        "Rusmiati, một cô gái quê giản dị, kết hôn với Badri, một người đàn ông đáng kính, mặc cho lời tiên tri rằng mối quan hệ của họ sẽ mang đến tai họa.",
                        "Johansyah Jumberan", "Rio Dewanto, Putri Intan, Ochi Rosdiana", "Tiếng Indo", "C-16"),
                betaMovie("Supergirl", "Hành động, Phiêu lưu", 108, LocalDate.of(2026, 6, 26), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f23%2f400x633%2D113318%2D230626%2D50.png", "https://www.youtube.com/watch?v=uy-dgT7_n9Q",
                        "Khi một kẻ thù bất ngờ tấn công quá gần nhà, Kara Zor-El, tức Supergirl, miễn cưỡng hợp tác với một người bạn đồng hành không ngờ tới trong hành trình báo thù và công lý giữa các vì sao.",
                        "Craig Gillespie", "Milly Alcock, Matthias Schoenaerts, Eve Ridley, David Krumholtz, Emily Beecham, Jason Momoa", "Tiếng Anh", "C-13"),
                betaMovie("Tên Cậu Là Gì", "Hoạt hình", 107, LocalDate.of(2026, 6, 5), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f05%2f18%2fyour%2Dname%2Dlocalized%2Dposter%2D0%2D140410%2D180526%2D51.png", "https://www.youtube.com/watch?v=wjXvr4o22pw",
                        "Taki và Mitsuha vô tình hoán đổi thân thể qua những giấc mơ, rồi cùng lần theo sợi dây định mệnh để tìm nhau giữa biến cố sao chổi.",
                        "Shinkai Makoto", "Kamiki Ryunosuke, Kamishiraishi Mone, Narita Ryo", "Tiếng Nhật", "C-13"),
                betaMovie("Thực Thể Quỷ Quyệt", "Kinh dị, Giật gân", 110, LocalDate.of(2026, 6, 26), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f11%2f260626%2Dblackrooms%2D163351%2D110626%2D30.jpg", "https://www.youtube.com/watch?v=8O1QNb01NFI",
                        "Sau khi bệnh nhân của một nhà trị liệu biến mất vào một chiều không gian nằm ngoài thực tại, cô phải dấn thân vào vùng đất bí ẩn để cứu anh ta.",
                        "Kane Parsons", "Chiwetel Ejiofor, Renate Reinsve, Mark Duplass", "Tiếng Anh", "C-16"),
                betaMovie("Trường Hè, 2001", "Gia đình, Kịch", 105, LocalDate.of(2026, 6, 19), Movie.MovieStatus.NOW_SHOWING,
                        "https://files.betacorp.vn/media%2fimages%2f2026%2f06%2f11%2f190626%2Dtruong%2Dhe%2D2001%2D150040%2D110626%2D22.jpg", "https://www.youtube.com/watch?v=FquQQa1A71g",
                        "Mùa hè năm 2001, Kiên trở về đoàn tụ với gia đình tại khu chợ nhộn nhịp ở thị trấn Cheb sau 10 năm xa cách, mở ra nhiều mâu thuẫn liên thế hệ và khao khát được thấu hiểu.",
                        "Dužan Duong", "Bùi Thế Dương, Hoang Anh Doan, Tô Tiến Tài, Lê Quỳnh Lan, Dung Nguyen", "Tiếng Việt", "C-18")
        );

        List<Movie> existingMovies = movieRepository.findAll();
        List<Movie> moviesToSave = betaMovies.stream()
                .map(betaMovie -> {
                    Optional<Movie> existingOpt = existingMovies.stream()
                            .filter(existing -> existing.getTitle().equalsIgnoreCase(betaMovie.getTitle()))
                            .findFirst();
                    if (existingOpt.isPresent()) {
                        return copyMovieData(existingOpt.get(), betaMovie);
                    }
                    return betaMovie;
                })
                .toList();
        movieRepository.saveAll(moviesToSave);
    }

    /**
     * Phương thức tạo đối tượng Movie cơ bản.
     */
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

    /**
     * Phương thức hỗ trợ tạo đối tượng Beta Movie với đầy đủ thông tin định dạng và trailer.
     */
    private Movie betaMovie(String title, String genre, Integer duration, LocalDate releaseDate,
                            Movie.MovieStatus status, String posterUrl, String trailerUrl,
                            String description, String director, String cast,
                            String language, String ageRating) {
        Movie movie = movie(title, genre, duration, releaseDate, status, posterUrl, posterUrl,
                description, director, cast, language, ageRating);
        movie.setTrailerUrl(trailerUrl);
        movie.setReleaseYear(releaseDate != null ? releaseDate.getYear() : null);
        movie.setProducer("Beta Cinemas");
        movie.setFormat("2D");
        return movie;
    }

    /**
     * Seed danh sách các bài viết tin tức mẫu (`Post`) nếu `postRepository.count() == 0`.
     */
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

    /**
     * Hỗ trợ tạo đối tượng Post nhanh.
     */
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

    /**
     * Seed các chiến dịch khuyến mãi (`Promotion`) nếu `promotionRepository.count() == 0`.
     */
    private void seedPromotions() {
        if (promotionRepository.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        promotionRepository.saveAll(List.of(
                promotion("Happy Monday - Giá vé nhẹ hơn mỗi thứ Hai",
                        Promotion.CampaignType.HAPPY_MONDAY,
                        Promotion.TargetGroup.ALL,
                        "Đồng giá vé 45.000đ cho các suất chiếu 2D vào thứ Hai hàng tuần.",
                        "Chiến dịch Happy Monday giúp khách hàng bắt đầu tuần mới bằng một buổi xem phim tiết kiệm, áp dụng cho các suất chiếu hợp lệ tại hệ thống rạp.",
                        "Áp dụng vào thứ Hai hàng tuần, không áp dụng cho suất chiếu đặc biệt, ghế đôi hoặc ngày lễ theo quy định của rạp.",
                        "Khách hàng chọn suất chiếu trong ngày thứ Hai trên website hoặc tại quầy, hệ thống/nhân viên sẽ kiểm tra điều kiện chiến dịch trước khi thanh toán.",
                        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1600&q=85",
                        today.minusDays(3), today.plusMonths(2)),
                promotion("Member Day - Ưu đãi dành riêng cho thành viên",
                        Promotion.CampaignType.MEMBER_DAY,
                        Promotion.TargetGroup.MEMBER,
                        "Thành viên nhận ưu đãi giá vé và combo vào thứ Tư hàng tuần.",
                        "Member Day là chiến dịch chăm sóc khách hàng thân thiết, khuyến khích khách đăng nhập và sử dụng tài khoản thành viên khi đặt vé.",
                        "Áp dụng cho tài khoản thành viên đang hoạt động. Mỗi khách hàng cần đăng nhập hoặc xuất trình thông tin thành viên khi mua vé.",
                        "Đăng nhập tài khoản thành viên trước khi đặt vé hoặc cung cấp số điện thoại thành viên tại quầy để được kiểm tra quyền lợi.",
                        "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=1600&q=85",
                        today.plusDays(2), today.plusMonths(3)),
                promotion("Student Screen - Ưu đãi học sinh sinh viên",
                        Promotion.CampaignType.STUDENT_DISCOUNT,
                        Promotion.TargetGroup.STUDENT,
                        "Học sinh, sinh viên được hưởng mức giá ưu đãi khi xuất trình thẻ hợp lệ.",
                        "Chiến dịch Student Screen tạo điều kiện để nhóm khách trẻ tiếp cận phim mới với chi phí hợp lý hơn trong các khung giờ phù hợp.",
                        "Khách hàng cần xuất trình thẻ học sinh/sinh viên còn hiệu lực. Không áp dụng đồng thời với chương trình ưu đãi khác.",
                        "Chọn suất chiếu hợp lệ, mang theo thẻ học sinh/sinh viên và xác nhận thông tin tại quầy trước khi nhận vé.",
                        "https://images.unsplash.com/photo-1524985069026-dd778a71c7b4?auto=format&fit=crop&w=1600&q=85",
                        today.minusDays(10), today.plusMonths(1)),
                promotion("Bank Weekend - Ưu đãi thanh toán ngân hàng",
                        Promotion.CampaignType.BANK_PROMOTION,
                        Promotion.TargetGroup.BANK_USER,
                        "Khách thanh toán bằng ngân hàng liên kết nhận ưu đãi theo từng cuối tuần.",
                        "Bank Weekend hỗ trợ các chiến dịch đồng thương hiệu với ngân hàng, giúp tăng tỷ lệ thanh toán không tiền mặt và doanh số cuối tuần.",
                        "Áp dụng theo danh sách ngân hàng liên kết từng thời điểm. Số lượng giao dịch ưu đãi có thể giới hạn theo ngày.",
                        "Chọn phương thức thanh toán ngân hàng liên kết khi đặt vé online hoặc hỏi nhân viên tại quầy để được hướng dẫn.",
                        "https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?auto=format&fit=crop&w=1600&q=85",
                        today.plusDays(5), today.plusMonths(2))
        ));
    }

    /**
     * Hỗ trợ tạo đối tượng Promotion nhanh.
     */
    private Promotion promotion(String title,
                                Promotion.CampaignType type,
                                Promotion.TargetGroup targetGroup,
                                String discountRule,
                                String description,
                                String conditionText,
                                String howToJoin,
                                String bannerImage,
                                LocalDate startDate,
                                LocalDate endDate) {
        Promotion promotion = new Promotion();
        promotion.setTitle(title);
        promotion.setType(type);
        promotion.setTargetGroup(targetGroup);
        promotion.setDiscountRule(discountRule);
        promotion.setDescription(description);
        promotion.setConditionText(conditionText);
        promotion.setHowToJoin(howToJoin);
        promotion.setBannerImage(bannerImage);
        promotion.setStartDate(startDate);
        promotion.setEndDate(endDate);
        promotion.setStatus(Promotion.PromotionStatus.ACTIVE);
        return promotion;
    }

    /**
     * Hỗ trợ sao chép thông tin từ bộ phim nguồn sang phim đích để cập nhật dữ liệu.
     */
    private Movie copyMovieData(Movie target, Movie source) {
        target.setGenre(source.getGenre());
        target.setDuration(source.getDuration());
        target.setReleaseDate(source.getReleaseDate());
        target.setPosterUrl(source.getPosterUrl());
        target.setTrailerUrl(source.getTrailerUrl());
        target.setSummary(source.getSummary());
        target.setDirector(source.getDirector());
        target.setActors(source.getActors());
        target.setLanguage(source.getLanguage());
        target.setAgeRating(source.getAgeRating());
        target.setFormat(source.getFormat());
        target.setStatus(source.getStatus());
        target.setActive(source.isActive());
        return target;
    }
}

