package com.nikita.crawler.parser;

import com.nikita.model.LinkRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkExtractor {
    private static final Pattern MOVIE_LINK_PATTERN =
            Pattern.compile(
                    "<a\\b[^>]*href\\s*=\\s*\"(/movie/[^\"/]+)\"[^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN =
            Pattern.compile("<[^>]+>");
    private static final String BASE_URL = "https://www.allmovie.com";

    public List<LinkRecord> extractMovieLinks(String html) {
        if (html == null) return List.of();

        Set<String> uniqueUrls = new LinkedHashSet<>();
        List<LinkRecord> links = new ArrayList<>();
        Matcher matcher = MOVIE_LINK_PATTERN.matcher(html);
        while (matcher.find()) {
            String url = BASE_URL + matcher.group(1);
            if (uniqueUrls.add(url)) {
                String anchorText = HTML_TAG_PATTERN
                        .matcher(matcher.group(2))
                        .replaceAll("")
                        .replaceAll("\\s+", " ")
                        .trim();
                links.add(new LinkRecord(url, anchorText));
            }
        }
        return links;
    }
}