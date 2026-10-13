package com.nikita;

import com.nikita.crawler.engine.CrawlerEngine;

public class App {
    public static void main(String[] args) throws Exception {
        CrawlerEngine engine = new CrawlerEngine(
                2000,                    // 2s politeness delay
                "data/raw_html",         // HTML output directory
                "data/documents.tsv",    // downloaded-page metadata
                "data/queue.tsv"         // pending/visited URL events
        );

        engine.start(
                "https://www.allmovie.com/movie/the-godfather-vm1069502",
                10
        );
    }
}
