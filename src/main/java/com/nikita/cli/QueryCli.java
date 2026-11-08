package com.nikita.cli;

import com.nikita.indexer.Indexer;
import com.nikita.lucene.LuceneSearcher;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class QueryCli {
    public void start() throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("1. Custom Index (Old)");
            System.out.println("2. Lucene Index (New)");
            System.out.print("Choose index: ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                searchCustom(scanner);
            } else if ("2".equals(choice)) {
                searchLucene(scanner);
            } else {
                System.err.println("Invalid index selection. Choose 1 or 2.");
            }
        }
    }

    private void searchCustom(Scanner scanner) throws IOException {
        Indexer indexer = new Indexer("data/extracted.tsv");
        while (true) {
            System.out.print("Enter query (or 'exit'): ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String query = scanner.nextLine();
            if ("exit".equalsIgnoreCase(query.trim())) {
                break;
            }
            printResults("--- Method 1 (Standard IDF) ---", indexer.search(query, 1));
            printResults("--- Method 2 (Smooth IDF) ---", indexer.search(query, 2));
        }
    }

    private void searchLucene(Scanner scanner) throws IOException {
        try (LuceneSearcher searcher = new LuceneSearcher(Path.of("data/lucene_index"))) {
            while (true) {
                System.out.print("Enter query (or 'exit'): ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String query = scanner.nextLine();
                if ("exit".equalsIgnoreCase(query.trim())) {
                    break;
                }
                try {
                    List<LuceneSearcher.SearchResult> results = searcher.search(query);
                    System.out.println("--- Lucene results ---");
                    if (results.isEmpty()) {
                        System.out.println("No results.");
                    }
                    results.forEach(result -> System.out.printf("%s | Score: %.6f | %s%n",
                            result.title(), result.score(), result.url()));
                } catch (ParseException e) {
                    System.err.println("Invalid Lucene query: " + e.getMessage());
                }
            }
        }
    }

    private void printResults(
            String heading,
            List<Indexer.SearchResult> results) {
        System.out.println(heading);
        results.stream()
                .limit(10)
                .forEach(result -> System.out.printf(
                        "%s | Score: %.6f | %s%n",
                        result.title(),
                        result.score(),
                        result.docUrl()));
    }
}
