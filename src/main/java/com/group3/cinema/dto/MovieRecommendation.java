package com.group3.cinema.dto;

import com.group3.cinema.entity.Movie;

public record MovieRecommendation(Movie movie, int recommendationScore, String reason) {
}
