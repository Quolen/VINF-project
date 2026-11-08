package com.nikita.crawler.storage;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.IntArrayList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class CrawlerStatistics {
    private static final Encoding TOKEN_ENCODING =
            Encodings.newLazyEncodingRegistry()
                    .getEncoding("cl100k_base")
                    .orElseThrow(() -> new IllegalStateException(
                            "cl100k_base encoding is unavailable"));
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
        printTokenAggregate();
    }

    public void printTokenizationExamples() throws IOException {
        if (!Files.exists(extractedFile)) {
            return;
        }

        System.out.println("Jtokkit tokenization examples (cl100k_base):");
        try (Stream<String> lines = Files.lines(extractedFile, StandardCharsets.UTF_8)) {
            lines.limit(3).forEach(this::printTokenizationExample);
        }
    }

    private void printTokenAggregate() throws IOException {
        if (!Files.exists(extractedFile)) {
            return;
        }

        long totalTokens = 0;
        int documentCount = 0;
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;

        try (Stream<String> lines = Files.lines(extractedFile, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                String[] columns = line.split("\t", -1);
                if (columns.length < 12) {
                    continue;
                }
                try {
                    int tokenCount = Integer.parseInt(columns[11]);
                    totalTokens += tokenCount;
                    documentCount++;
                    minimum = Math.min(minimum, tokenCount);
                    maximum = Math.max(maximum, tokenCount);
                } catch (NumberFormatException ignored) {
                    // Ignore incomplete or malformed historical rows.
                }
            }
        }

        if (documentCount > 0) {
            System.out.printf(
                    "Tokens (jtokkit cl100k_base): total=%d, average=%.2f, min=%d, max=%d%n",
                    totalTokens,
                    (double) totalTokens / documentCount,
                    minimum,
                    maximum);
        }
    }

    private void printTokenizationExample(String line) {
        String[] columns = line.split("\t", -1);
        if (columns.length < 12) {
            return;
        }

        String combinedText = String.join(" ", columns[1], columns[7], columns[5]);
        IntArrayList tokenIds = TOKEN_ENCODING.encode(combinedText);
        List<String> tokenPieces = new ArrayList<>();
        int displayedTokens = Math.min(tokenIds.size(), 20);
        for (int i = 0; i < displayedTokens; i++) {
            IntArrayList singleToken = new IntArrayList(1);
            singleToken.add(tokenIds.get(i));
            tokenPieces.add(tokenIds.get(i) + "=\"" + TOKEN_ENCODING.decode(singleToken) + "\"");
        }

        System.out.println("  URL: " + columns[0]);
        System.out.println("  Text: " + combinedText);
        System.out.println("  Token count: " + tokenIds.size());
        System.out.println("  First " + displayedTokens + " tokens: "
                + String.join(", ", tokenPieces));
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
