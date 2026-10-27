package com.nikita.indexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Indexer {
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();
    private final Map<String, String> documentTitles = new HashMap<>();
    private final Tokenizer tokenizer = new Tokenizer();
    private int totalDocs;

    public Indexer(String extractedFile) throws IOException {
        load(Path.of(extractedFile));
    }

    private void load(Path extractedFile) throws IOException {
        if (!Files.exists(extractedFile)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(
                extractedFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", -1);
                if (columns.length < 8) {
                    continue;
                }

                String url = columns[0].trim();
                if (url.isEmpty()) {
                    continue;
                }

                String title = columns[1];
                String director;
                String leadCast;
                String genres;
                String synopsis;
                String themes;

                if (columns.length >= 11) {
                    director = columns[3];
                    leadCast = columns[4];
                    genres = columns[5];
                    synopsis = columns[7];
                    themes = columns[8];
                } else {
                    // Compatibility with extracted.tsv files written before leadCast.
                    director = columns[3];
                    leadCast = "";
                    genres = columns[4];
                    synopsis = columns[6];
                    themes = columns[7];
                }

                documentTitles.put(url, title);
                indexDocument(url, String.join(" ",
                        title, director, leadCast, genres, synopsis, themes));
                totalDocs++;
            }
        }
    }

    private void indexDocument(String url, String text) {
        Map<String, Integer> termFrequencies = new HashMap<>();
        for (String term : tokenizer.tokenize(text)) {
            termFrequencies.merge(term, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
            invertedIndex
                    .computeIfAbsent(entry.getKey(), ignored -> new HashMap<>())
                    .put(url, entry.getValue());
        }
    }

    public List<SearchResult> search(String query, int idfMethod) {
        if (idfMethod != 1 && idfMethod != 2) {
            throw new IllegalArgumentException("IDF method must be 1 or 2");
        }

        Map<String, Double> scores = new HashMap<>();
        for (String term : tokenizer.tokenize(query)) {
            Map<String, Integer> documents = invertedIndex.get(term);
            if (documents == null || documents.isEmpty()) {
                continue;
            }

            int documentFrequency = documents.size();
            double idf = idfMethod == 1
                    ? Math.log((double) totalDocs / documentFrequency)
                    : Math.log(1.0 + ((double) totalDocs / documentFrequency));

            for (Map.Entry<String, Integer> document : documents.entrySet()) {
                double score = document.getValue() * idf;
                scores.merge(document.getKey(), score, Double::sum);
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Double> score : scores.entrySet()) {
            results.add(new SearchResult(
                    score.getKey(),
                    documentTitles.getOrDefault(score.getKey(), ""),
                    score.getValue()));
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed()
                .thenComparing(SearchResult::title)
                .thenComparing(SearchResult::docUrl));
        return results.subList(0, Math.min(10, results.size()));
    }

    public int totalDocs() {
        return totalDocs;
    }

    public record SearchResult(String docUrl, String title, double score) {
    }
}
