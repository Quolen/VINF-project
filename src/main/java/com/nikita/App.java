package com.nikita;

import com.nikita.cli.QueryCli;
import com.nikita.crawler.engine.CrawlerEngine;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("search")) {
            new QueryCli().start();
            return;
        }

        int limit = args.length > 0 && args[0].equals("crawl")
                ? 15_000
                : 5;

        CrawlerEngine engine = new CrawlerEngine(
                2000,                    // 2s politeness delay
                "data/raw_html",         // HTML output directory
                "data/documents.tsv",    // downloaded-page metadata
                "data/queue.tsv",        // pending/visited URL events
                "data/extracted.tsv"     // extracted movie entities
        );

        engine.start(
                "https://www.allmovie.com/movie/storm-at-daybreak-am107130",
                limit
        );
    }
}
