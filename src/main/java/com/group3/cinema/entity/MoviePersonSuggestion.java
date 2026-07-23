/**
 * Entity lưu trữ gợi ý tên Đạo diễn, Nhà sản xuất, Diễn viên (`movie_person_suggestions`).
 * 
 * Phục vụ tính năng gợi ý tự động (Autocomplete) cho Admin/Manager khi thêm hoặc chỉnh sửa phim.
 * Phân loại (`type`): DIRECTOR (Đạo diễn), PRODUCER (Nhà sản xuất), ACTOR (Diễn viên).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "movie_person_suggestions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "type"})
})
public class MoviePersonSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    /** Loại nhân sự: DIRECTOR | PRODUCER | ACTOR */
    @Column(nullable = false, columnDefinition = "NVARCHAR(50)")
    private String type;

    public MoviePersonSuggestion() {
    }

    public MoviePersonSuggestion(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

