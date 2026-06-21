package com.group3.cinema.controller.api;

import com.group3.cinema.entity.MoviePersonSuggestion;
import com.group3.cinema.repository.MoviePersonSuggestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions/persons")
@CrossOrigin(origins = "*")
public class MoviePersonSuggestionController {

    private final MoviePersonSuggestionRepository suggestionRepository;

    @Autowired
    public MoviePersonSuggestionController(MoviePersonSuggestionRepository suggestionRepository) {
        this.suggestionRepository = suggestionRepository;
    }

    @GetMapping
    public ResponseEntity<List<MoviePersonSuggestion>> getSuggestions(@RequestParam("type") String type) {
        List<MoviePersonSuggestion> suggestions = suggestionRepository.findByTypeIgnoreCase(type);
        return ResponseEntity.ok(suggestions);
    }
}
