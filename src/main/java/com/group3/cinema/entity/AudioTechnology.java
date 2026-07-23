/**
 * Entity quản lý danh mục Công nghệ Âm thanh phòng chiếu (`audio_technologies`).
 * 
 * Ví dụ: Dolby 7.1, Dolby Atmos, DTS:X.
 * Được chọn khi khởi tạo thông số phòng chiếu (`Room`).
 * 
 * Khởi tạo bởi: NinhDD - HE186113 (04/06/2026)
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "audio_technologies")
public class AudioTechnology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, columnDefinition = "NVARCHAR(80)")
    private String name;

    @Column(name = "description", columnDefinition = "NVARCHAR(255)")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public AudioTechnology() {
    }

    public AudioTechnology(Long id, String name, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lớp Builder giúp tạo nhanh đối tượng AudioTechnology.
     */
    public static class Builder {
        private final AudioTechnology audioTechnology = new AudioTechnology();

        public Builder id(Long id) {
            audioTechnology.setId(id);
            return this;
        }

        public Builder name(String name) {
            audioTechnology.setName(name);
            return this;
        }

        public Builder description(String description) {
            audioTechnology.setDescription(description);
            return this;
        }

        public Builder active(boolean active) {
            audioTechnology.setActive(active);
            return this;
        }

        public AudioTechnology build() {
            return audioTechnology;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

