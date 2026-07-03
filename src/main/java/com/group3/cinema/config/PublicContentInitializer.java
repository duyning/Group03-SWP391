package com.group3.cinema.config;

/*
 * Created on 2026-06-09: Seed customer-facing demo movies and news when tables are empty.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.entity.Post;
import com.group3.cinema.entity.Promotion;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.MovieRepository;
import com.group3.cinema.repository.PostRepository;
import com.group3.cinema.repository.PromotionRepository;
import com.group3.cinema.repository.api.ShowtimeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@DependsOn("roomUnicodeMigration")
public class PublicContentInitializer {

    private final MovieRepository movieRepository;
    private final PostRepository postRepository;
    private final PromotionRepository promotionRepository;
    private final ShowtimeRepository showtimeRepository;
    private final JdbcTemplate jdbcTemplate;

    public PublicContentInitializer(MovieRepository movieRepository, PostRepository postRepository,
                                    PromotionRepository promotionRepository,
                                    ShowtimeRepository showtimeRepository, JdbcTemplate jdbcTemplate) {
        this.movieRepository = movieRepository;
        this.postRepository = postRepository;
        this.promotionRepository = promotionRepository;
        this.showtimeRepository = showtimeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    @Transactional
    public void seedPublicContent() {
        seedMovies();
        seedShowtimes();
        seedPosts();
        seedPromotions();
    }

    private void seedMovies() {
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

        java.util.Set<String> betaTitles = betaMovies.stream()
                .map(Movie::getTitle)
                .collect(java.util.stream.Collectors.toSet());
        List<Movie> existingMovies = movieRepository.findAll();
        existingMovies.stream()
                .filter(movie -> !betaTitles.contains(movie.getTitle()))
                .forEach(movie -> movie.setActive(false));
        movieRepository.saveAll(existingMovies);
        deleteUnreferencedNonBetaMovies(betaTitles);

        movieRepository.saveAll(betaMovies.stream()
                .map(betaMovie -> existingMovies.stream()
                        .filter(existing -> existing.getTitle().equals(betaMovie.getTitle()))
                        .findFirst()
                        .map(existing -> copyMovieData(existing, betaMovie))
                        .orElse(betaMovie))
                .toList());
    }

    private void deleteUnreferencedNonBetaMovies(java.util.Set<String> betaTitles) {
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(betaTitles.size(), "?"));
            String sql = """
                    DELETE FROM movie
                    WHERE title NOT IN (%s)
                      AND NOT EXISTS (SELECT 1 FROM showtimes WHERE showtimes.movie_id = movie.id)
                      AND NOT EXISTS (SELECT 1 FROM tickets WHERE tickets.movie_id = movie.id)
                    """.formatted(placeholders);
            jdbcTemplate.update(sql, betaTitles.toArray());
        } catch (Exception ignored) {
            // If existing movie rows are referenced, they stay hidden instead of breaking booking history.
        }
    }

    private void seedShowtimes() {
        List<Movie> movies = movieRepository.findByActiveTrue().stream()
                .sorted(java.util.Comparator.comparing(Movie::getTitle))
                .toList();
        if (movies.isEmpty()) {
            return;
        }

        java.util.Set<String> existingSlots = showtimeRepository.findAll().stream()
                .map(this::showtimeKey)
                .collect(java.util.stream.Collectors.toSet());

        List<String> rooms = jdbcTemplate.query(
                "SELECT room_name FROM rooms WHERE status = N'Hoạt động' ORDER BY room_name",
                (rs, rowNum) -> rs.getString("room_name").trim()
        );
        if (rooms.isEmpty()) {
            rooms = List.of("Phòng Demo 01", "Seed Room 01", "Seed Room 04"); // Fallback to database active rooms
        }

        List<List<LocalTime>> roomSlots = List.of(
                List.of(LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 0), LocalTime.of(21, 0)),
                List.of(LocalTime.of(9, 30), LocalTime.of(12, 30), LocalTime.of(15, 30), LocalTime.of(18, 30), LocalTime.of(21, 30)),
                List.of(LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(22, 0)),
                List.of(LocalTime.of(10, 30), LocalTime.of(13, 30), LocalTime.of(16, 30), LocalTime.of(19, 30)),
                List.of(LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(17, 0), LocalTime.of(20, 0))
        );

        LocalDate startDate = LocalDate.now().plusDays(1);
        List<Showtime> newShowtimes = new java.util.ArrayList<>();
        int movieCursor = 0;

        for (int dayOffset = 0; dayOffset < 14; dayOffset++) {
            LocalDate showDate = startDate.plusDays(dayOffset);
            for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
                String room = rooms.get(roomIndex);
                List<LocalTime> slots = roomSlots.get(roomIndex % roomSlots.size());
                for (LocalTime showTime : slots) {
                    Movie movie = movies.get(movieCursor % movies.size());
                    movieCursor++;

                    Showtime showtime = new Showtime();
                    showtime.setMovie(movie);
                    showtime.setShowDate(showDate);
                    showtime.setShowTime(showTime);
                    showtime.setRoom(room);
                    showtime.setDayType(determineDayType(showDate));

                    String key = showtimeKey(showtime);
                    if (existingSlots.add(key)) {
                        newShowtimes.add(showtime);
                    }
                }
            }
        }

        if (!newShowtimes.isEmpty()) {
            showtimeRepository.saveAll(newShowtimes);
        }
    }

    private String showtimeKey(Showtime showtime) {
        return (showtime.getRoom() == null ? "" : showtime.getRoom().trim().toLowerCase())
                + "|" + showtime.getShowDate()
                + "|" + showtime.getShowTime();
    }

    private String determineDayType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        if ((month == 1 && day == 1) || (month == 4 && day == 30)
                || (month == 5 && day == 1) || (month == 9 && day == 2)) {
            return "Ngày lễ";
        }
        java.time.DayOfWeek dow = date.getDayOfWeek();
        return dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY
                ? "Cuối tuần"
                : "Trong tuần";
    }

    private void seedDemoMoviesUnused() {
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

    private Movie copyMovieData(Movie target, Movie source) {
        target.setGenre(source.getGenre());
        target.setDuration(source.getDuration());
        target.setReleaseDate(source.getReleaseDate());
        target.setPosterUrl(source.getPosterUrl());
        target.setBannerUrl(source.getBannerUrl());
        target.setTrailerUrl(source.getTrailerUrl());
        target.setDescription(source.getDescription());
        target.setDirector(source.getDirector());
        target.setCast(source.getCast());
        target.setLanguage(source.getLanguage());
        target.setAgeRating(source.getAgeRating());
        target.setReleaseYear(source.getReleaseYear());
        target.setProducer(source.getProducer());
        target.setStatus(source.getStatus());
        target.setFormat(source.getFormat());
        target.setActive(true);
        return target;
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
}
