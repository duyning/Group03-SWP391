package example.entity;

/*
 * Added on 2026-06-04: Movie entity for cinema movie data.
 * Created by: HuyPB - HE191335
 */

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Represents a movie shown in the cinema system.
 *
 * Business flow:
 * - Admin or seed data stores movie information in the movie table.
 * - Customer-facing screens only display movies where active = true.
 * - status controls which UI section the movie appears in:
 *   NOW_SHOWING, COMING_SOON, or SPECIAL_SCREENING.
 * - posterUrl is used for card images; bannerUrl is used for large hero/detail backgrounds.
 */
@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String title;

    @Column(columnDefinition = "NVARCHAR(100)")
    private String genre;

    private int duration;

    private LocalDate releaseDate;

    private String posterUrl;

    private String bannerUrl;

    private String trailerUrl;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(columnDefinition = "NVARCHAR(255)")
    private String director;

    /*
     * Mapped to movie_cast instead of cast because "cast" can be confusing in SQL
     * and may conflict with SQL function naming.
     */
    @Column(name = "movie_cast", columnDefinition = "NVARCHAR(MAX)")
    private String cast;

    @Column(columnDefinition = "NVARCHAR(100)")
    private String language;

    @Column(columnDefinition = "NVARCHAR(50)")
    private String ageRating;

    /*
     * Stored as text so database values remain readable and stable even if enum
     * order changes later.
     */
    @Enumerated(EnumType.STRING)
    private MovieStatus status;

    /*
     * Soft-display flag:
     * true  = movie is visible to customers.
     * false = movie stays in database but is hidden from public movie pages.
     */
    private boolean active;

    public Movie() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getCast() {
        return cast;
    }

    public void setCast(String cast) {
        this.cast = cast;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAgeRating() {
        return ageRating;
    }

    public void setAgeRating(String ageRating) {
        this.ageRating = ageRating;
    }

    public MovieStatus getStatus() {
        return status;
    }

    public void setStatus(MovieStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public enum MovieStatus {
        /*
         * Movie is currently available on public listing and booking flows.
         */
        NOW_SHOWING,

        /*
         * Movie is announced before release; shown in upcoming movie sections.
         */
        COMING_SOON,

        /*
         * Movie belongs to a limited/special screening group.
         */
        SPECIAL_SCREENING
    }
}
