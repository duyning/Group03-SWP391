package com.group3.cinema.dto;

/*
 * Added on 2026-07-10: View model for recommended movies shown on customer movie pages.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Movie;

/** Combines the movie, matching percentage, and human-readable recommendation reason. */
public record MovieRecommendation(Movie movie, int recommendationScore, String reason) {
}
