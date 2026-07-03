package com.group3.cinema.entity;

/*
 * Added on 2026-06-04: Movie entity for cinema movie data.
 * Created by: HuyPB - HE191335
 * Updated on 2026-06-23 by: TrienLX
 * Chi tiết thay đổi:
 * - Bổ sung trạng thái STOPPED ("Ngừng chiếu") vào enum MovieStatus
 * - Bổ sung logic phân tích cú pháp cho STOPPED trong fromJson.
 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.text.Normalizer;
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

    private Integer duration;

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

    private Integer releaseYear;

    @Column(columnDefinition = "NVARCHAR(255)")
    private String producer;

    /*
     * Stored as text so database values remain readable and stable even if enum
     * order changes later.
     */
    @Enumerated(EnumType.STRING)
    private MovieStatus status;

    @Column(columnDefinition = "NVARCHAR(50)")
    private String format;

    /*
     * Soft-display flag:
     * true  = movie is visible to customers.
     * false = movie stays in database but is hidden from public movie pages.
     */
    private boolean active = true;

    private boolean deleted = false;

    public Movie() {
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
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

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
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

    public String getSummary() {
        return description;
    }

    public void setSummary(String summary) {
        this.description = summary;
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

    public String getActors() {
        return cast;
    }

    public void setActors(String actors) {
        this.cast = actors;
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

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public enum MovieStatus {
        /*
         * Movie is currently available on public listing and booking flows.
         */
        NOW_SHOWING("Đang chiếu"),

        /*
         * Movie is announced before release; shown in upcoming movie sections.
         */
        COMING_SOON("Sắp chiếu"),

        /*
         * Movie belongs to a limited/special screening group.
         */
        SPECIAL_SCREENING("Suất chiếu đặc biệt"),

        /*
         * Movie has stopped screening or is hidden.
         */
        STOPPED("Ngừng chiếu");

        private final String displayName;

        MovieStatus(String displayName) {
            this.displayName = displayName;
        }

        @JsonValue
        public String getDisplayName() {
            return displayName;
        }

        @JsonCreator
        public static MovieStatus fromJson(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }

            String normalized = normalize(value);

            for (MovieStatus status : values()) {
                if (status.name().equalsIgnoreCase(normalized)
                        || status.displayName.equalsIgnoreCase(normalized)) {
                    return status;
                }
            }

            String lower = normalized.toLowerCase();
            if (lower.contains("dang") || lower.contains("showing")) {
                return NOW_SHOWING;
            }
            if (lower.contains("sap") || lower.contains("soon") || lower.contains("upcoming")) {
                return COMING_SOON;
            }
            if (lower.contains("special") || lower.contains("dac biet")) {
                return SPECIAL_SCREENING;
            }
            if (lower.contains("stop") || lower.contains("ngung") || lower.contains("ngung chieu")) {
                return STOPPED;
            }

            return null;
        }

        private static String normalize(String value) {
            String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            return normalized
                    .replace('Đ', 'D')
                    .replace('đ', 'd');
        }
    }
}
