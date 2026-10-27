package com.nikita.crawler.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

public class CrawlerStatistics {
    private final Path documentsFile;
    private final Path rawHtmlDirectory;
    private final Path extractedFile;

    public CrawlerStatistics(
            String documentsFile,
            String rawHtmlDirectory,
            String extractedFile) {
        this.documentsFile = Path.of(documentsFile);
        this.rawHtmlDirectory = Path.of(rawHtmlDirectory);
        this.extractedFile = Path.of(extractedFile);
    }

    public void printStatistics() throws IOException {
        long documentCount = countLines(documentsFile);
        long rawHtmlBytes = calculateDirectorySize(rawHtmlDirectory);
        long extractedBytes = Files.exists(extractedFile)
                ? Files.size(extractedFile)
                : 0L;

        System.out.printf("Downloaded documents: %d%n", documentCount);
        System.out.printf("Raw HTML size: %.2f MB%n", toMegabytes(rawHtmlBytes));
        System.out.printf("Extracted TSV size: %.2f MB%n", toMegabytes(extractedBytes));
    }

    private static long countLines(Path file) throws IOException {
        if (!Files.exists(file)) {
            return 0L;
        }
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        }
    }

    private static long calculateDirectorySize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            long total = 0L;
            Iterator<Path> iterator = paths
                    .filter(Files::isRegularFile)
                    .iterator();
            while (iterator.hasNext()) {
                total += Files.size(iterator.next());
            }
            return total;
        }
    }

    private static double toMegabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
