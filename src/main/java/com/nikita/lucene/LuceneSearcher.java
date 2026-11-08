package com.nikita.lucene;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LuceneSearcher implements Closeable {
    private final FSDirectory directory;
    private final DirectoryReader reader;
    private final StandardAnalyzer analyzer;
    private final IndexSearcher searcher;
    private final MultiFieldQueryParser parser;

    public LuceneSearcher(Path indexPath) throws IOException {
        if (!java.nio.file.Files.isDirectory(indexPath)) {
            throw new IOException("Lucene index not found at " + indexPath + "; run lucene-index first");
        }
        directory = FSDirectory.open(indexPath);
        try {
            reader = DirectoryReader.open(directory);
        } catch (IOException | RuntimeException e) {
            directory.close();
            throw e;
        }
        analyzer = new StandardAnalyzer();
        searcher = new IndexSearcher(reader);
        parser = new MultiFieldQueryParser(new String[]{"title", "synopsis", "wiki_text"}, analyzer);
    }

    public List<SearchResult> search(String queryString) throws IOException, ParseException {
        if (queryString == null || queryString.isBlank()) {
            return List.of();
        }
        Query query = parser.parse(queryString);
        List<SearchResult> results = new ArrayList<>();
        for (ScoreDoc hit : searcher.search(query, 10).scoreDocs) {
            Document document = searcher.storedFields().document(hit.doc, Set.of("url", "title"));
            results.add(new SearchResult(document.get("url"), document.get("title"), hit.score));
        }
        return results;
    }

    @Override
    public void close() throws IOException {
        try {
            reader.close();
        } finally {
            try {
                directory.close();
            } finally {
                analyzer.close();
            }
        }
    }

    public record SearchResult(String url, String title, float score) {
    }
}
