package com.nikita.crawler.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileStorage {
    private final Path htmlDir;
    private final Path documentsFile;
    private final Path queueFile;

    public FileStorage(String htmlDir, String documentsFile, String queueFile)
            throws IOException {
        this.htmlDir = Path.of(htmlDir);
        this.documentsFile = Path.of(documentsFile);
        this.queueFile = Path.of(queueFile);

        Files.createDirectories(this.htmlDir);
        createParentAndFile(this.documentsFile);
        createParentAndFile(this.queueFile);
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

    private static void createParentAndFile(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
    }
}