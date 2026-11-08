package com.nikita.spark;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.io.Serializable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.lower;
import static org.apache.spark.sql.functions.monotonically_increasing_id;
import static org.apache.spark.sql.functions.not;
import static org.apache.spark.sql.functions.substring;
import static org.apache.spark.sql.functions.trim;
import static org.apache.spark.sql.functions.udf;

public class WikiParser {
    private static final Logger LOG = LoggerFactory.getLogger(WikiParser.class);
    private static final String PAGE_START = "<page>";
    private static final String PAGE_END = "</page>";
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("<text[^>]*>(.*?)</text>", Pattern.DOTALL);
    private static final Pattern REDIRECT = Pattern.compile("^\\s*#REDIRECT\\b", Pattern.CASE_INSENSITIVE);
    private static final String EXCLUDED_NAMESPACES =
            "(?i)^(category|template|file|wikipedia|talk|user|help|portal|draft|module|mediawiki):.*";
    private static final Pattern PARENTHESIZED = Pattern.compile("\\([^()]*\\)");
    private static final String[] MOVIE_COLUMNS = {
            "url", "title", "year", "director", "cast", "genres", "runtime",
            "synopsis", "themes", "mpaa", "country", "tokens"
    };
    private static final StructType SCHEMA = new StructType()
            .add("title", DataTypes.StringType, false)
            .add("text", DataTypes.StringType, false);

    public Dataset<Row> parseWikipediaDump(SparkSession spark, String inputPath, String outputPath) {
        JavaRDD<String> lines = spark.sparkContext().textFile(inputPath, 8).toJavaRDD();
        Dataset<Row> pages = parseLines(spark, lines);
        pages.filter(not(pages.col("title").rlike(EXCLUDED_NAMESPACES)))
                .write().mode("overwrite").parquet(outputPath);
        Dataset<Row> articles = spark.read().parquet(outputPath);
        long count = articles.count();
        LOG.info("Extracted {} non-redirect Wikipedia pages without excluded namespace prefixes to {}", count, outputPath);
        return articles;
    }

    public void joinData(SparkSession spark, String movieTsvPath, String wikiParquetPath, String outputPath)
            throws IOException {
        Path destination = new Path(outputPath);
        FileSystem fs = destination.getFileSystem(spark.sparkContext().hadoopConfiguration());
        if (fs.exists(destination)) {
            throw new IOException("Join output already exists; refusing to overwrite: " + destination);
        }

        Dataset<Row> movies = spark.read()
                .option("sep", "\t")
                .option("header", "false")
                .option("quote", "\u0000")
                .option("mode", "FAILFAST")
                .csv(movieTsvPath)
                .toDF(MOVIE_COLUMNS);
        UDF1<String, String> cleanTitle = title -> title == null
                ? null : PARENTHESIZED.matcher(title).replaceAll("").trim();
        Dataset<Row> movieKeys = movies
                .withColumn("_movie_id", monotonically_increasing_id())
                .withColumn("clean_title", lower(trim(udf(cleanTitle, DataTypes.StringType).apply(col("title")))))
                .alias("movies");
        Dataset<Row> wiki = spark.read().parquet(wikiParquetPath)
                .select(col("title").alias("_wiki_original_title"),
                        lower(trim(col("title"))).alias("wiki_title"),
                        col("text").alias("wiki_text"))
                .alias("wiki");
        Dataset<Row> joined = movieKeys.join(wiki,
                movieKeys.col("clean_title").equalTo(wiki.col("wiki_title")), "left");

        long totalMovies = movies.count();
        Row stats = joined.filter(col("wiki_text").isNotNull())
                .agg(countDistinct(col("_movie_id")), countDistinct(col("_wiki_original_title")))
                .first();
        LOG.info("AllMovie movies: {}; matched movies: {}; unique Wikipedia pages joined: {}",
                totalMovies, stats.getLong(0), stats.getLong(1));
        joined.filter(col("wiki_text").isNotNull())
                .select(movieKeys.col("title").alias("movie_title"),
                        substring(col("wiki_text"), 1, 200).alias("wiki_snippet"))
                .show(3, false);

        Column[] outputColumns = new Column[MOVIE_COLUMNS.length + 1];
        for (int i = 0; i < MOVIE_COLUMNS.length; i++) {
            outputColumns[i] = movieKeys.col(MOVIE_COLUMNS[i]);
        }
        outputColumns[MOVIE_COLUMNS.length] = wiki.col("wiki_text");
        Dataset<Row> output = joined.select(outputColumns);

        Path parent = destination.getParent();
        if (parent == null) {
            parent = new Path(".");
        }
        Path staging = new Path(parent, "." + destination.getName() + ".spark-" + UUID.randomUUID());
        output.coalesce(1).write()
                .option("sep", "\t")
                .option("header", "true")
                .option("quote", "\"")
                .option("escape", "\"")
                .option("ignoreLeadingWhiteSpace", "false")
                .option("ignoreTrailingWhiteSpace", "false")
                .csv(staging.toString());
        FileStatus[] parts = fs.globStatus(new Path(staging, "part-*.csv"));
        if (parts == null || parts.length != 1) {
            throw new IOException("Expected one join output part in " + staging);
        }
        if (fs.exists(destination) || !fs.rename(parts[0].getPath(), destination)) {
            throw new IOException("Could not move join output to " + destination + "; staging preserved at " + staging);
        }
        if (!fs.delete(staging, true)) {
            throw new IOException("Join output saved, but could not remove Spark staging directory " + staging);
        }
        LOG.info("Saved joined TSV to {}", destination);
    }

    Dataset<Row> parseLines(SparkSession spark, JavaRDD<String> lines) {
        // A small first pass identifies open pages and page IDs at partition boundaries.
        List<PartitionSummary> summaries = new ArrayList<>(lines.mapPartitionsWithIndex((index, input) -> {
            long starts = 0;
            boolean hasEvent = false;
            boolean lastEventIsStart = false;
            while (input.hasNext()) {
                String line = input.next();
                int position = 0;
                while (position < line.length()) {
                    int start = line.indexOf(PAGE_START, position);
                    int end = line.indexOf(PAGE_END, position);
                    if (start < 0 && end < 0) {
                        break;
                    }
                    lastEventIsStart = start >= 0 && (end < 0 || start < end);
                    hasEvent = true;
                    if (lastEventIsStart) {
                        starts++;
                        position = start + PAGE_START.length();
                    } else {
                        position = end + PAGE_END.length();
                    }
                }
            }
            return List.of(new PartitionSummary(index, starts, hasEvent, lastEventIsStart)).iterator();
        }, true).collect());
        summaries.sort(Comparator.comparingInt(PartitionSummary::index));

        long[] startsBefore = new long[summaries.size()];
        boolean[] openBefore = new boolean[summaries.size()];
        long totalStarts = 0;
        boolean open = false;
        for (PartitionSummary summary : summaries) {
            startsBefore[summary.index()] = totalStarts;
            openBefore[summary.index()] = open;
            totalStarts += summary.starts();
            if (summary.hasEvent()) {
                open = summary.lastEventIsStart();
            }
        }

        JavaPairRDD<Long, Tuple2<Integer, String>> fragments = lines.mapPartitionsWithIndex(
                (index, input) -> new PageFragmentIterator(
                        input, index, startsBefore[index], openBefore[index]), true)
                .mapToPair(fragment -> fragment);

        JavaRDD<Row> rows = fragments.groupByKey().values().flatMap(parts -> {
            List<Tuple2<Integer, String>> ordered = new ArrayList<>();
            parts.forEach(ordered::add);
            ordered.sort(Comparator.comparingInt(Tuple2::_1));
            StringBuilder page = new StringBuilder();
            ordered.forEach(part -> page.append(part._2()));
            if (page.indexOf(PAGE_START) != 0
                    || page.lastIndexOf(PAGE_END) != page.length() - PAGE_END.length()) {
                return List.<Row>of().iterator();
            }
            Matcher title = TITLE.matcher(page);
            Matcher text = TEXT.matcher(page);
            if (!title.find() || !text.find() || REDIRECT.matcher(text.group(1)).find()) {
                return List.<Row>of().iterator();
            }
            return List.of(RowFactory.create(title.group(1), text.group(1))).iterator();
        });
        return spark.createDataFrame(rows, SCHEMA);
    }

    private record PartitionSummary(int index, long starts, boolean hasEvent,
                                    boolean lastEventIsStart) implements Serializable {
    }

    private static final class PageFragmentIterator implements Iterator<Tuple2<Long, Tuple2<Integer, String>>> {
        private final Iterator<String> input;
        private final int partition;
        private long nextPage;
        private long pageId;
        private boolean open;
        private String line;
        private int position;
        private StringBuilder fragment;
        private Tuple2<Long, Tuple2<Integer, String>> next;

        private PageFragmentIterator(Iterator<String> input, int partition, long startsBefore, boolean openBefore) {
            this.input = input;
            this.partition = partition;
            this.nextPage = startsBefore;
            this.open = openBefore;
            this.pageId = startsBefore - 1;
            this.fragment = openBefore ? new StringBuilder() : null;
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            while (true) {
                if (line == null) {
                    if (!input.hasNext()) {
                        if (open && fragment.length() > 0) {
                            next = emit();
                            open = false;
                            return true;
                        }
                        return false;
                    }
                    line = input.next();
                    position = 0;
                }
                if (!open) {
                    int start = line.indexOf(PAGE_START, position);
                    if (start < 0) {
                        line = null;
                        continue;
                    }
                    open = true;
                    pageId = nextPage++;
                    fragment = new StringBuilder();
                    position = start;
                }
                int end = line.indexOf(PAGE_END, position);
                if (end >= 0) {
                    fragment.append(line, position, end + PAGE_END.length());
                    position = end + PAGE_END.length();
                    open = false;
                    next = emit();
                    return true;
                }
                fragment.append(line, position, line.length()).append('\n');
                line = null;
            }
        }

        private Tuple2<Long, Tuple2<Integer, String>> emit() {
            return new Tuple2<>(pageId, new Tuple2<>(partition, fragment.toString()));
        }

        @Override
        public Tuple2<Long, Tuple2<Integer, String>> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Tuple2<Long, Tuple2<Integer, String>> result = next;
            next = null;
            return result;
        }
    }
}
