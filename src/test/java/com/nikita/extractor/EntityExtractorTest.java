package com.nikita.extractor;

import com.nikita.model.Movie;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityExtractorTest {
    @Test
    void extractsRequiredFieldsFromFirstTwentyPages() throws IOException {
        List<Path> htmlFiles;
        try (var paths = Files.list(Path.of("data/raw_html"))) {
            htmlFiles = paths
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted()
                    .limit(20)
                    .toList();
        }

        assertTrue(htmlFiles.size() >= 20,
                "At least 20 raw HTML pages are required for this test");

        EntityExtractor extractor = new EntityExtractor();
        for (Path htmlFile : htmlFiles) {
            String html = Files.readString(htmlFile);
            Movie movie = extractor.extract(html, htmlFile.toUri().toString());

            assertNotNull(movie.title(), htmlFile.toString());
            assertFalse(movie.title().isEmpty(), htmlFile.toString());
            assertNotNull(movie.releaseYear(), htmlFile.toString());
            assertFalse(movie.releaseYear().isEmpty(), htmlFile.toString());
            assertNotNull(movie.synopsis(), htmlFile.toString());
            assertFalse(movie.synopsis().isEmpty(), htmlFile.toString());
        }
    }
}
