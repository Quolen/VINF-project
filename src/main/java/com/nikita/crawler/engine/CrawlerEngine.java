package com.nikita.crawler.engine;

import com.nikita.crawler.http.HttpFetcher;
import com.nikita.crawler.parser.LinkExtractor;
import com.nikita.crawler.storage.CrawlerStatistics;
import com.nikita.crawler.storage.FileStorage;
import com.nikita.extractor.EntityExtractor;
import com.nikita.model.LinkRecord;
import com.nikita.model.Movie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

public class CrawlerEngine {
    private static final Logger log = LoggerFactory.getLogger(CrawlerEngine.class);

    private final UrlFrontier frontier;
    private final HttpFetcher fetcher;
    private final LinkExtractor linkExtractor = new LinkExtractor();
    private final EntityExtractor entityExtractor = new EntityExtractor();
    private final UrlFilter urlFilter = new UrlFilter();
    private final FileStorage storage;
    private final CrawlerStatistics statistics;

    public CrawlerEngine(
            long politenessMs,
            String htmlDir,
            String documentsFile,
            String queueFile,
            String extractedFile) throws IOException {
        this.fetcher = new HttpFetcher(politenessMs);
        this.storage = new FileStorage(
                htmlDir, documentsFile, queueFile, extractedFile);
        this.statistics = new CrawlerStatistics(
                documentsFile, htmlDir, extractedFile);
        this.frontier = new UrlFrontier(storage.readDocumentUrls());
        for (String pendingUrl : storage.readPendingUrls()) {
            frontier.add(pendingUrl);
        }
    }

    public void start(String seedUrl, int limit) throws InterruptedException, IOException {
        start(List.of(seedUrl), limit);
    }

    public void start(Collection<String> seedUrls, int limit)
            throws InterruptedException, IOException {
        for (String seedUrl : seedUrls) {
            if (urlFilter.isAllowed(seedUrl) && frontier.add(seedUrl)) {
                storage.appendQueueEvent(seedUrl, "", "PENDING");
            }
        }

        int downloaded = 0;

        while (!frontier.isEmpty() && downloaded < limit) {
            String url = frontier.poll();
            if (url == null) continue;

            String html = fetcher.fetchHtml(url);
            if (html == null) continue;

            storage.saveHtml(url, html);
            Movie movie = entityExtractor.extract(html, url);
            storage.appendExtracted(movie);
            log.info("Extracted: {}", movie.title());
            storage.appendDocument(url, String.valueOf(html.length()), sha256(html));
            downloaded++;

            if (downloaded % 100 == 0) {
                log.info("Downloaded {}/{} (frontier={}, visited={})",
                        downloaded, limit,
                        frontier.queueSize(), frontier.visitedCount());
                statistics.printStatistics();
            } else if (limit < 15_000) {
                log.info("Downloaded {}/{} (frontier={}, visited={})",
                        downloaded, limit,
                        frontier.queueSize(), frontier.visitedCount());
            }

            for (LinkRecord link : linkExtractor.extractMovieLinks(html)) {
                if (urlFilter.isAllowed(link.url())
                        && frontier.add(link.url())) {
                    storage.appendQueueEvent(
                            link.url(),
                            link.anchorText(),
                            "PENDING");
                }
            }
        }

        log.info("Crawl finished. Total downloaded: {}", downloaded);
        statistics.printStatistics();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (Exception e) {
            return "";
        }
    }
}