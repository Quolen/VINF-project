package com.nikita.crawler.storage;

import com.nikita.model.Movie;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class FileStorage {
    private final Path htmlDir;
    private final Path documentsFile;
    private final Path queueFile;
    private final Path extractedFile;

    public FileStorage(
            String htmlDir,
            String documentsFile,
            String queueFile,
            String extractedFile)
            throws IOException {
        this.htmlDir = Path.of(htmlDir);
        this.documentsFile = Path.of(documentsFile);
        this.queueFile = Path.of(queueFile);
        this.extractedFile = Path.of(extractedFile);

        Files.createDirectories(this.htmlDir);
        createParentAndFile(this.documentsFile);
        createParentAndFile(this.queueFile);
        createParentAndFile(this.extractedFile);
    }

    public void saveHtml(String url, String html) throws IOException {
        String fileName = url.replaceAll("[^a-zA-Z0-9]", "_") + ".html";
        Files.writeString(htmlDir.resolve(fileName), html);
    }

    public void appendDocument(String url, String sizeBytes, String hash)
            throws IOException {
        String line = String.join("\t", url, sizeBytes, hash) + System.lineSeparator();
        Files.writeString(documentsFile, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void appendQueueEvent(String url, String anchorText, String status)
            throws IOException {
        String line = String.join("\t", url, anchorText, status)
                + System.lineSeparator();
        Files.writeString(queueFile, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void appendExtracted(Movie movie) throws IOException {
        String line = String.join("\t",
                sanitize(movie.url()),
                sanitize(movie.title()),
                sanitize(movie.releaseYear()),
                sanitize(movie.director()),
                sanitize(movie.leadCast()),
                sanitize(movie.genres()),
                sanitize(movie.runtime()),
                sanitize(movie.synopsis()),
                sanitize(movie.themes()),
                sanitize(movie.mpaaRating()),
                sanitize(movie.country()),
                String.valueOf(movie.tokenCount()))
                + System.lineSeparator();
        Files.writeString(extractedFile, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public List<String> readDocumentUrls() throws IOException {
        return readUrls(documentsFile, false);
    }

    public List<String> readPendingUrls() throws IOException {
        return readUrls(queueFile, true);
    }

    private static List<String> readUrls(Path file, boolean pendingOnly)
            throws IOException {
        List<String> urls = new ArrayList<>();
        if (!Files.exists(file)) {
            return urls;
        }

        for (String line : Files.readAllLines(file)) {
            String[] columns = line.split("\t", -1);
            if (columns.length >= 1
                    && (!pendingOnly
                    || (columns.length >= 3 && "PENDING".equals(columns[2])))) {
                String url = columns[0].trim();
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private static void createParentAndFile(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
    }

    private static String sanitize(String value) {
        return value == null
                ? ""
                : value.replace('\t', ' ')
                        .replace('\r', ' ')
                        .replace('\n', ' ');
    }
}