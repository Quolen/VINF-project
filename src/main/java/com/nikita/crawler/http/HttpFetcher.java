package com.nikita.crawler.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpFetcher {
    private static final Logger log = LoggerFactory.getLogger(HttpFetcher.class);

    private final HttpClient client;
    private final long politenessDelayMs;

    public HttpFetcher(long politenessDelayMs) {
        this.politenessDelayMs = politenessDelayMs;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String fetchHtml(String url) throws InterruptedException {
        try {
            Thread.sleep(politenessDelayMs);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

            log.info("Fetching: {}", url);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.warn("Non-200 status {} for {}", response.statusCode(), url);
                return null;
            }
        } catch (IOException e) {
            log.error("IO error for {}: {}", url, e.getMessage());
            return null;
        }
    }
}