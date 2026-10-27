package com.nikita.model;

public record Movie(
        String url,
        String title,
        String releaseYear,
        String synopsis,
        String director,
        String leadCast,
        String genres,
        String themes,
        String mpaaRating,
        String runtime,
        String country,
        int tokenCount) {
}
