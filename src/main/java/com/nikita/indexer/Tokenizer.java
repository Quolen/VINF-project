package com.nikita.indexer;

import java.util.Arrays;
import java.util.List;

public class Tokenizer {
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(normalized.split("\\s+"));
    }
}
