package com.nikita;

import com.nikita.cli.QueryCli;
import com.nikita.crawler.engine.CrawlerEngine;
import com.nikita.lucene.LuceneIndexer;
import com.nikita.spark.WikiParser;
import org.apache.spark.sql.SparkSession;

import java.nio.file.Path;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("lucene-index")) {
            new LuceneIndexer().build(Path.of("data/joined_data.tsv"), Path.of("data/lucene_index"));
            return;
        }

        if (args.length > 0 && args[0].equals("spark-join")) {
            SparkSession spark = SparkSession.builder()
                    .appName("AllMovie Wikipedia join")
                    .master(System.getProperty("spark.master", "local[2]"))
                    .config("spark.sql.parquet.columnarReaderBatchSize", "64")
                    .getOrCreate();
            try {
                new WikiParser().joinData(spark, "data/extracted.tsv",
                        "data/wiki_parquet", "data/joined_data.tsv");
            } finally {
                spark.stop();
            }
            return;
        }

        if (args.length > 0 && args[0].equals("spark-parse")) {
            SparkSession spark = SparkSession.builder()
                    .appName("Wikipedia dump parser")
                    .master(System.getProperty("spark.master", "local[*]"))
                    .getOrCreate();
            try {
                new WikiParser().parseWikipediaDump(spark,
                        "data/enwiki-latest-pages-articles1.xml-p1p41242.bz2",
                        "data/wiki_parquet");
            } finally {
                spark.stop();
            }
            return;
        }

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
