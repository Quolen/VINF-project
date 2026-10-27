package com.nikita.extractor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.nikita.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityExtractor {
    private static final Encoding TOKEN_ENCODING =
            Encodings.newLazyEncodingRegistry()
                    .getEncoding("cl100k_base")
                    .orElseThrow(() -> new IllegalStateException(
                            "cl100k_base encoding is unavailable"));
    private static final Pattern JSON_LD_PATTERN = Pattern.compile(
            "<script\\s+type=\"application/ld\\+json\"\\s*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SYNOPSIS_PATTERN = Pattern.compile(
            "<section\\s+class=\"review\\s+read-more\\s+description\">.*?"
                    + "<div\\s+class=\"text\">(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GENRES_PATTERN = Pattern.compile(
            "<span\\s+class=\"header-movie-genres\">.*?Genres\\s*-\\s*(.*?)"
                    + "&nbsp;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern THEMES_PATTERN = Pattern.compile(
            "<div\\s+class=\"themes\">.*?<div\\s+class=\"charactList\">"
                    + "(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MPAA_PATTERN = Pattern.compile(
            "MPAA\\s+Rating\\s*-\\s*<span>(.*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RUNTIME_PATTERN = Pattern.compile(
            "Run\\s+Time\\s*-\\s*<span>(.*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COUNTRY_PATTERN = Pattern.compile(
            "Countries\\s*-\\s*<span>(.*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    public Movie extract(String html, String url) {
        JsonObject jsonLd = extractJsonLd(html);

        String title = jsonString(jsonLd, "name");
        String releaseYear = jsonString(jsonLd, "datePublished");
        String director = extractDirector(jsonLd);
        String leadCast = extractLeadCast(jsonLd);
        String genres = extractGenres(jsonLd);
        String runtime = jsonString(jsonLd, "duration");

        String htmlGenres = extractFirst(html, GENRES_PATTERN, true);
        if (genres.isEmpty()) {
            genres = htmlGenres;
        }
        String synopsis = extractFirst(html, SYNOPSIS_PATTERN, true);
        int tokenCount = TOKEN_ENCODING.countTokens(
                String.join(" ", title, synopsis, genres));

        return new Movie(
                value(url),
                title,
                releaseYear,
                synopsis,
                director,
                leadCast,
                genres,
                extractThemes(html),
                extractFirst(html, MPAA_PATTERN, true),
                runtime.isEmpty() ? extractFirst(html, RUNTIME_PATTERN, true) : runtime,
                extractFirst(html, COUNTRY_PATTERN, true),
                tokenCount);
    }

    private static JsonObject extractJsonLd(String html) {
        Matcher matcher = JSON_LD_PATTERN.matcher(value(html));
        if (!matcher.find()) {
            return new JsonObject();
        }
        try {
            JsonElement element = JsonParser.parseString(matcher.group(1).trim());
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return new JsonObject();
        }
    }

    private static String extractDirector(JsonObject jsonLd) {
        JsonElement director = jsonLd.get("director");
        if (director == null || !director.isJsonArray() || director.getAsJsonArray().isEmpty()) {
            return "";
        }
        JsonElement first = director.getAsJsonArray().get(0);
        return first.isJsonObject() ? jsonString(first.getAsJsonObject(), "name") : "";
    }

    private static String extractGenres(JsonObject jsonLd) {
        JsonElement genre = jsonLd.get("genre");
        if (genre == null) {
            return "";
        }
        if (genre.isJsonArray()) {
            List<String> genres = new ArrayList<>();
            for (JsonElement item : genre.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    genres.add(item.getAsString().trim());
                }
            }
            return String.join(", ", genres);
        }
        return genre.isJsonPrimitive() ? genre.getAsString().trim() : "";
    }

    private static String extractThemes(String html) {
        String raw = extractFirst(html, THEMES_PATTERN, false);
        Matcher matcher = Pattern.compile(
                "<a\\b[^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(raw);
        List<String> themes = new ArrayList<>();
        while (matcher.find()) {
            String theme = cleanText(matcher.group(1));
            if (!theme.isEmpty()) {
                themes.add(theme);
            }
        }
        return String.join(", ", themes);
    }

    private static String extractLeadCast(JsonObject jsonLd) {
        JsonElement actor = jsonLd.get("actor");
        if (actor == null || !actor.isJsonArray()) {
            actor = jsonLd.get("actors");
        }
        if (actor == null || !actor.isJsonArray()) {
            return "";
        }
        List<String> cast = new ArrayList<>();
        JsonArray actors = actor.getAsJsonArray();
        for (JsonElement item : actors) {
            if (cast.size() == 3) {
                break;
            }
            if (item.isJsonObject()) {
                String name = jsonString(item.getAsJsonObject(), "name");
                if (!name.isEmpty()) {
                    cast.add(name);
                }
            }
        }
        return String.join(", ", cast);
    }

    private static String extractFirst(
            String html,
            Pattern pattern,
            boolean stripTags) {
        Matcher matcher = pattern.matcher(value(html));
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1);
        return stripTags ? cleanText(value) : value.trim();
    }

    private static String jsonString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString().trim()
                : "";
    }

    private static String cleanText(String text) {
        return HTML_TAG_PATTERN.matcher(value(text))
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
