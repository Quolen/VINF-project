package com.nikita.spark;

import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scala.Tuple2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SparkEnvironmentTest {
    @Test
    void processesLocalInput(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("input.txt");
        Files.write(input, List.of("start", "second", "third"));

        SparkConf conf = new SparkConf()
                .setAppName("Spark environment test")
                .setMaster("local[2]")
                .set("spark.ui.enabled", "false")
                .set("spark.driver.host", "127.0.0.1")
                .set("spark.driver.bindAddress", "127.0.0.1");

        try (JavaSparkContext spark = new JavaSparkContext(conf)) {
            var matches = spark.textFile(input.toUri().toString())
                    .filter(line -> line.startsWith("s"));
            assertEquals(2, matches.count());
            Path output = tempDir.resolve("output");
            matches.saveAsTextFile(output.toUri().toString());
            assertEquals(2, spark.textFile(output.toUri().toString()).count());

            Path compressed = tempDir.resolve("wiki.txt.bz2");
            BZip2Codec codec = new BZip2Codec();
            codec.setConf(spark.hadoopConfiguration());
            try (OutputStream stream = codec.createOutputStream(Files.newOutputStream(compressed))) {
                stream.write("matrix\nalien\n".getBytes(StandardCharsets.UTF_8));
            }
            var wikiTitles = spark.textFile(compressed.toUri().toString())
                    .mapToPair(title -> new Tuple2<>(title, "wiki"));
            var movieTitles = spark.parallelizePairs(List.of(
                    new Tuple2<>("matrix", "movie"),
                    new Tuple2<>("other", "movie")));
            assertEquals(1, movieTitles.join(wikiTitles).count());
        }
    }
}
