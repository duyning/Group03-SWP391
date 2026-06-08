package com.group3.cinema.controller.api;

/**
 * Dá»± Ã¡n: Cinema 2026 â€” SWP391 Group 03
 * File: MovieController.java
 * Chá»©c nÄƒng: REST Controller cung cáº¥p cÃ¡c API Ä‘á»ƒ quáº£n lÃ½ phim (Movie).
 *            Há»— trá»£ hiá»ƒn thá»‹ danh sÃ¡ch, tÃ¬m kiáº¿m & lá»c nÃ¢ng cao, thÃªm má»›i, sá»­a Ä‘á»•i, xÃ³a phim vÃ 
 *            thá»‘ng kÃª tá»•ng sá»‘ lÆ°á»£ng phim theo tá»«ng tráº¡ng thÃ¡i.
 * Endpoints:
 *   - GET /api/movies: Láº¥y danh sÃ¡ch phim theo bá»™ lá»c (Query Params: title, genre, director, duration, status, releaseDate).
 *   - GET /api/movies/{id}: Xem thÃ´ng tin chi tiáº¿t cá»§a má»™t bá»™ phim theo ID.
 *   - POST /api/movies: ThÃªm phim má»›i.
 *   - PUT /api/movies/{id}: Cáº­p nháº­t thÃ´ng tin phim.
 *   - DELETE /api/movies/{id}: XÃ³a má»™t bá»™ phim.
 *   - GET /api/movies/stats: Thá»‘ng kÃª sá»‘ lÆ°á»£ng phim (tá»•ng sá»‘, Ä‘ang chiáº¿u, sáº¯p chiáº¿u, suáº¥t Ä‘áº·c biá»‡t).
 * NgÆ°á»i viáº¿t: TrienLX - HE182285
 * NgÃ y táº¡o: 2026-06-04
 */

import com.group3.cinema.entity.Movie;
import com.group3.cinema.service.api.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// ÄÃ¡nh dáº¥u lá»›p nÃ y lÃ  má»™t RestController Ä‘á»ƒ Spring Boot Ä‘á»‹nh cáº¥u hÃ¬nh cÃ¡c API endpoint tráº£ vá» dá»¯ liá»‡u JSON
@RestController("apiMovieController")
// Äá»‹nh nghÄ©a Ä‘Æ°á»ng dáº«n cÆ¡ sá»Ÿ (base path) cho toÃ n bá»™ API trong controller nÃ y lÃ  "/api/movies"
@RequestMapping("/api/movies")
// Cho phÃ©p táº¥t cáº£ cÃ¡c nguá»“n gá»‘c (Cross-Origin Resource Sharing) gá»i Ä‘áº¿n API nÃ y (Ä‘á»ƒ trÃ¡nh lá»—i CORS)
@CrossOrigin(origins = "*")
public class MovieController {

    // Khai bÃ¡o káº¿t ná»‘i Ä‘áº¿n MovieService xá»­ lÃ½ nghiá»‡p vá»¥
    private final MovieService movieService;

    // TiÃªm (Inject) MovieService thÃ´ng qua Constructor Injection
    @Autowired
    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    // Endpoint: GET /api/movies
    // Há»— trá»£ tÃ¬m kiáº¿m, lá»c phim nÃ¢ng cao báº±ng cÃ¡ch truyá»n tham sá»‘ tÃ¹y chá»n (Query Parameters)
    @GetMapping
    public ResponseEntity<List<Movie>> searchMovies(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "director", required = false) String director,
            @RequestParam(value = "duration", required = false) Integer duration,
            @RequestParam(value = "status", required = false) String status,
            // Há»— trá»£ parse Ä‘á»‹nh dáº¡ng ngÃ y chuáº©n ISO (yyyy-MM-dd) thÃ nh kiá»ƒu dá»¯ liá»‡u LocalDate
            @RequestParam(value = "releaseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate releaseDate
    ) {
        List<Movie> movies = movieService.searchMovies(title, genre, director, duration, status, releaseDate);
        return ResponseEntity.ok(movies); // Tráº£ vá» mÃ£ pháº£n há»“i HTTP 200 OK kÃ¨m danh sÃ¡ch phim
    }

    // Endpoint: GET /api/movies/{id}
    // Láº¥y thÃ´ng tin chi tiáº¿t cá»§a má»™t bá»™ phim theo mÃ£ ID truyá»n vÃ o tá»« Ä‘Æ°á»ng dáº«n (Path Variable)
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable("id") Integer id) {
        return movieService.getMovieById(id)
                .map(ResponseEntity::ok) // Náº¿u tÃ¬m tháº¥y, tráº£ vá» HTTP 200 OK kÃ¨m dá»¯ liá»‡u phim
                .orElse(ResponseEntity.notFound().build()); // Náº¿u khÃ´ng tÃ¬m tháº¥y, tráº£ vá» HTTP 404 Not Found
    }

    // Endpoint: POST /api/movies
    // ThÃªm má»™t bá»™ phim má»›i vÃ o cÆ¡ sá»Ÿ dá»¯ liá»‡u. Dá»¯ liá»‡u phim Ä‘Æ°á»£c gá»­i trong thÃ¢n Request (Request Body) á»Ÿ dáº¡ng JSON
    @PostMapping
    public ResponseEntity<Movie> createMovie(@RequestBody Movie movie) {
        Movie savedMovie = movieService.saveMovie(movie);
        return ResponseEntity.ok(savedMovie); // Tráº£ vá» HTTP 200 OK kÃ¨m Ä‘á»‘i tÆ°á»£ng phim Ä‘Ã£ Ä‘Æ°á»£c lÆ°u thÃ nh cÃ´ng
    }

    // Endpoint: PUT /api/movies/{id}
    // Cáº­p nháº­t thÃ´ng tin phim Ä‘Ã£ tá»“n táº¡i theo mÃ£ ID
    @PutMapping("/{id}")
    public ResponseEntity<Movie> updateMovie(@PathVariable("id") Integer id, @RequestBody Movie movie) {
        try {
            Movie updated = movieService.updateMovie(id, movie);
            return ResponseEntity.ok(updated); // Tráº£ vá» HTTP 200 OK kÃ¨m thÃ´ng tin phim sau khi sá»­a Ä‘á»•i thÃ nh cÃ´ng
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // Tráº£ vá» HTTP 404 Not Found náº¿u ID phim khÃ´ng tá»“n táº¡i
        }
    }

    // Endpoint: DELETE /api/movies/{id}
    // XÃ³a bá»™ phim khá»i há»‡ thá»‘ng theo mÃ£ ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMovie(@PathVariable("id") Integer id) {
        try {
            movieService.deleteMovie(id);
            return ResponseEntity.noContent().build(); // Tráº£ vá» HTTP 204 No Content náº¿u xÃ³a thÃ nh cÃ´ng
        } catch (Exception e) {
            return ResponseEntity.notFound().build(); // Tráº£ vá» HTTP 404 Not Found náº¿u gáº·p lá»—i
        }
    }

    // Endpoint: GET /api/movies/stats
    // Tráº£ vá» sá»‘ liá»‡u thá»‘ng kÃª tá»•ng sá»‘ lÆ°á»£ng phim vÃ  phim theo tá»«ng tráº¡ng thÃ¡i hiá»ƒn thá»‹
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getMovieStats() {
        return ResponseEntity.ok(movieService.getMovieStats()); // Tráº£ vá» HTTP 200 OK kÃ¨m map dá»¯ liá»‡u thá»‘ng kÃª dáº¡ng JSON
    }
}
