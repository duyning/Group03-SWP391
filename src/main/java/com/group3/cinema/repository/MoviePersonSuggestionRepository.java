package com.group3.cinema.repository;

import com.group3.cinema.entity.MoviePersonSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoviePersonSuggestionRepository extends JpaRepository<MoviePersonSuggestion, Integer> {
    List<MoviePersonSuggestion> findByTypeIgnoreCase(String type);
    Optional<MoviePersonSuggestion> findByNameIgnoreCaseAndTypeIgnoreCase(String name, String type);
}
