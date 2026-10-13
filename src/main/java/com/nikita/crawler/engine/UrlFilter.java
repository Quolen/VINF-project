package com.nikita.crawler.engine;

import java.util.regex.Pattern;

public class UrlFilter {
    private static final Pattern FORBIDDEN_PATH_PATTERN = Pattern.compile(
            "^https?://(?:www\\.)?allmovie\\.com/(?:"
                    + "admin(?:/|$)"
                    + "|search(?:/|$)"
                    + "|user/"
                    + "|rating/"
                    + "|favorite/"
                    + "|mobile(?:/|$)"
                    + "|check_logged_in(?:[/?]|$)"
                    + "|fetch_scroll(?:[/?]|$)"
                    + "|movie/fetch_review_reviews/(?:.*)"
                    + "|movie/fetch_review_view/(?:.*)"
                    + "|ajax_cast_crew(?:/|$)"
                    + ")",
            Pattern.CASE_INSENSITIVE);

    public boolean isAllowed(String url) {
        return url != null && !FORBIDDEN_PATH_PATTERN.matcher(url).find();
    }
}
