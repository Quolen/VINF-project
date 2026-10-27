package com.nikita.cli;

import com.nikita.indexer.Indexer;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class QueryCli {
    public void start() throws IOException {
        Indexer indexer = new Indexer("data/extracted.tsv");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Enter query (or 'exit'): ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String query = scanner.nextLine();
                if ("exit".equalsIgnoreCase(query.trim())) {
                    break;
                }

                printResults(
                        "--- Method 1 (Standard IDF) ---",
                        indexer.search(query, 1));
                printResults(
                        "--- Method 2 (Smooth IDF) ---",
                        indexer.search(query, 2));
            }
        }
    }

    private void printResults(
            String heading,
            List<Indexer.SearchResult> results) {
        System.out.println(heading);
        results.stream()
                .limit(5)
                .forEach(result -> System.out.printf(
                        "%s | Score: %.6f | %s%n",
                        result.title(),
                        result.score(),
                        result.docUrl()));
    }
}
