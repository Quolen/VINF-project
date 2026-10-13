package com.nikita.crawler.engine;

import com.nikita.crawler.http.HttpFetcher;
import com.nikita.crawler.parser.LinkExtractor;
import com.nikita.crawler.storage.FileStorage;
import com.nikita.model.LinkRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

public class CrawlerEngine {
    private static final Logger log = LoggerFactory.getLogger(CrawlerEngine.class);

    private final UrlFrontier frontier = new UrlFrontier();
    private final HttpFetcher fetcher;
    private final LinkExtractor linkExtractor = new LinkExtractor();
    private final UrlFilter urlFilter = new UrlFilter();
    private final FileStorage storage;

    public CrawlerEngine(
            long politenessMs,
            String htmlDir,
            String documentsFile,
            String queueFile) throws IOException {
        this.fetcher = new HttpFetcher(politenessMs);
        this.storage = new FileStorage(htmlDir, documentsFile, queueFile);
    }

    public void start(String seedUrl, int limit) throws InterruptedException, IOException {
        if (urlFilter.isAllowed(seedUrl) && frontier.add(seedUrl)) {
            storage.appendQueueEvent(seedUrl, "", "PENDING");
        }

        int downloaded = 0;

        while (!frontier.isEmpty() && downloaded < limit) {
            String url = frontier.poll();
            if (url == null) continue;

            String html = fetcher.fetchHtml(url);
            if (html == null) continue;

            storage.saveHtml(url, html);
            storage.appendDocument(url, String.valueOf(html.length()), sha256(html));
            downloaded++;

            log.info("Downloaded {}/{} (frontier={}, visited={})",
                    downloaded, limit, frontier.queueSize(), frontier.visitedCount());

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