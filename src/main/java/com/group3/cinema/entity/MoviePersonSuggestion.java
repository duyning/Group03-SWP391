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

    @Column(nullable = false, columnDefinition = "NVARCHAR(50)")
    private String type; // "DIRECTOR", "PRODUCER", "ACTOR"

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
