package com.nikita.spark;

import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WikiParserTest {
    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        spark = SparkSession.builder()
                .appName("Wikipedia parser test")
                .master("local[2]")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void joinsPagesAcrossPartitionBoundaries() {
        JavaSparkContext context = JavaSparkContext.fromSparkContext(spark.sparkContext());
        List<String> lines = List.of(
                "<mediawiki>", "<page>", "<title>Cross boundary</title>",
                "<text xml:space=\"preserve\">first", "second", "third</text>",
                "</page>", "<page><title>Next</title><text>body</text></page>",
                "<page><title>Redirect</title><text>  #rEdIrEcT [[Next]]</text></page>",
                "<page><title>No text</title></page>", "</mediawiki>",
                "<page><title>Incomplete</title><text>not closed</text>");
        Map<String, String> articles = new WikiParser()
                .parseLines(spark, context.parallelize(lines, 12))
                .collectAsList().stream()
                .collect(Collectors.toMap(row -> row.getString(0), row -> row.getString(1)));

        assertEquals(Map.of("Cross boundary", "first\nsecond\nthird", "Next", "body"), articles);
    }

    @Test
    void readsBzip2AndWritesParquet(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("wiki.xml.bz2");
        BZip2Codec codec = new BZip2Codec();
        codec.setConf(spark.sparkContext().hadoopConfiguration());
        try (OutputStream output = codec.createOutputStream(Files.newOutputStream(input))) {
            output.write(("<mediawiki>\n<page>\n<title>Movie</title>\n"
                    + "<text bytes=\"3\">one\ntwo</text>\n</page>\n"
                    + "<page><title>Redirect</title><text>#REDIRECT [[Movie]]</text></page>\n"
                    + "<page><title>Category:Films</title><text>category</text></page>\n"
                    + "<page><title>tEmPlAtE:Film</title><text>template</text></page>\n"
                    + "<page><title>File:Film</title><text>file</text></page>\n"
                    + "<page><title>Wikipedia:Film</title><text>wikipedia</text></page>\n"
                    + "<page><title>Talk:Film</title><text>talk</text></page>\n"
                    + "<page><title>User:Film</title><text>user</text></page>\n"
                    + "<page><title>Help:Film</title><text>help</text></page>\n"
                    + "<page><title>Portal:Film</title><text>portal</text></page>\n"
                    + "<page><title>Draft:Film</title><text>draft</text></page>\n"
                    + "<page><title>Module:Film</title><text>module</text></page>\n"
                    + "<page><title>MediaWiki:Film</title><text>mediawiki</text></page>\n"
                    + "<page><title>Film: The Sequel</title><text>article</text></page>\n"
                    + "<page><title>Supercategory: Film</title><text>article</text></page>\n"
                    + "</mediawiki>\n").getBytes(StandardCharsets.UTF_8));
        }
        Path output = tempDir.resolve("parquet");
        var result = new WikiParser().parseWikipediaDump(spark, input.toUri().toString(), output.toUri().toString());
        assertEquals(List.of("title", "text"), List.of(result.columns()));
        var expected = Map.of("Movie", "one\ntwo", "Film: The Sequel", "article",
                "Supercategory: Film", "article");
        assertEquals(expected, result.collectAsList().stream()
                .collect(Collectors.toMap(row -> row.getString(0), row -> row.getString(1))));
        assertEquals(result.collectAsList(), spark.read().parquet(output.toUri().toString()).collectAsList());
    }

    @Test
    void joinsMoviesAndPreservesMultilineWikipediaText(@TempDir Path tempDir) throws IOException {
        Path movies = tempDir.resolve("movies.tsv");
        Files.writeString(movies,
                "url1\tThe Godfather (1972)\t1972\tDirector\t\tDrama\t175\tSynopsis\t\tR\tUS\t10\n"
                + "url2\tTHE GODFATHER (film)\t1972\t\t\tDrama\t\t\t\t\t\t0\n"
                + "url3\tUnmatched\t2001\t\t\t\t\t\t\t\t\t0\n"
                + "url4\t\"Quoted\" Movie\t2002\t\t\t\t\t\t\t\t\t0\n",
                StandardCharsets.UTF_8);
        String wikiText = "  First line\nSecond\tline with \"quotes\" and \\slashes  ";
        StructType schema = new StructType().add("title", DataTypes.StringType)
                .add("text", DataTypes.StringType);
        Path wiki = tempDir.resolve("wiki");
        spark.createDataFrame(List.of(
                RowFactory.create(" the godfather ", wikiText),
                RowFactory.create("\"Quoted\" Movie", "other text")), schema)
                .write().parquet(wiki.toUri().toString());
        Path output = tempDir.resolve("joined.tsv");

        new WikiParser().joinData(spark, movies.toUri().toString(), wiki.toUri().toString(),
                output.toUri().toString());
        var result = spark.read().option("sep", "\t").option("header", "true")
                .option("multiLine", "true").option("escape", "\"")
                .option("maxCharsPerColumn", "-1")
                .csv(output.toUri().toString());
        assertEquals(List.of("url", "title", "year", "director", "cast", "genres", "runtime",
                "synopsis", "themes", "mpaa", "country", "tokens", "wiki_text"), List.of(result.columns()));
        Map<String, Row> rows = result.collectAsList().stream()
                .collect(Collectors.toMap(row -> row.getAs("url"), row -> row));
        assertEquals(4, rows.size());
        assertEquals(wikiText, rows.get("url1").getAs("wiki_text"));
        assertEquals(wikiText, rows.get("url2").getAs("wiki_text"));
        assertNull(rows.get("url3").getAs("wiki_text"));
        assertEquals("\"Quoted\" Movie", rows.get("url4").getAs("title"));
        assertEquals("other text", rows.get("url4").getAs("wiki_text"));
        assertThrows(IOException.class, () -> new WikiParser().joinData(
                spark, movies.toUri().toString(), wiki.toUri().toString(), output.toUri().toString()));
    }

    @Test
    @EnabledIfSystemProperty(named = "wiki.parquet.check", matches = "true")
    void verifiesDownloadedDumpOutput() {
        var result = spark.read().parquet("data/wiki_parquet");
        assertEquals(List.of("title", "text"), List.of(result.columns()));
        assertEquals(Long.parseLong(System.getProperty("wiki.expected.count")), result.count());
        assertEquals(0, result.filter(result.col("title").isNull().or(result.col("text").isNull())).count());
        assertEquals(0, result.filter(result.col("title").rlike(
                "(?i)^(category|template|file|wikipedia|talk|user|help|portal|draft|module|mediawiki):.*"))
                .count());
        String expectedColonCount = System.getProperty("wiki.expected.colon.count");
        if (expectedColonCount != null) {
            assertEquals(Long.parseLong(expectedColonCount),
                    result.filter(result.col("title").contains(":")).count());
        }
    }
}
