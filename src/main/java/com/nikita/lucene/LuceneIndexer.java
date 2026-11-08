package com.nikita.lucene;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class LuceneIndexer {
    private static final Logger LOG = LoggerFactory.getLogger(LuceneIndexer.class);
    private static final List<String> COLUMNS = List.of(
            "url", "title", "year", "director", "cast", "genres", "runtime",
            "synopsis", "themes", "mpaa", "country", "tokens", "wiki_text");

    public long build(Path input, Path indexPath) throws IOException {
        CSVFormat format = CSVFormat.TDF.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .setIgnoreSurroundingSpaces(false)
                .build();
        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            if (!parser.getHeaderNames().equals(COLUMNS)) {
                throw new IOException("Unexpected joined TSV header: " + parser.getHeaderNames());
            }
            if (Files.exists(indexPath)) {
                throw new IOException("Index path already exists; refusing to overwrite: " + indexPath);
            }
            Path destination = indexPath.toAbsolutePath();
            Files.createDirectories(destination.getParent());
            Path staging = Files.createTempDirectory(destination.getParent(), ".lucene-index-");
            try {
                long count = writeIndex(parser, staging);
                Files.move(staging, destination);
                LOG.info("Indexed {} joined movie records in {}", count, indexPath);
                return count;
            } finally {
                if (Files.exists(staging)) {
                    try (Stream<Path> paths = Files.walk(staging)) {
                        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                            Files.delete(path);
                        }
                    }
                }
            }
        }
    }

    private long writeIndex(CSVParser parser, Path indexPath) throws IOException {
        try (FSDirectory directory = FSDirectory.open(indexPath);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            long count = 0;
            for (CSVRecord record : parser) {
                if (record.size() != COLUMNS.size() || record.get("url").isBlank()) {
                    throw new IOException("Invalid joined TSV record " + record.getRecordNumber()
                            + ": expected 13 fields and a non-empty URL");
                }
                Document document = new Document();
                document.add(new StringField("url", record.get("url"), Field.Store.YES));
                document.add(new TextField("title", record.get("title"), Field.Store.YES));
                document.add(new TextField("synopsis", record.get("synopsis"), Field.Store.YES));
                document.add(new TextField("genres", record.get("genres"), Field.Store.YES));
                document.add(new TextField("wiki_text", record.get("wiki_text"), Field.Store.YES));
                writer.addDocument(document);
                count++;
            }
            writer.commit();
            return count;
        }
    }
}
