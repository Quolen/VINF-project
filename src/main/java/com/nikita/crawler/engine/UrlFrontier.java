package com.nikita.crawler.engine;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Collection;
import java.util.Queue;
import java.util.Set;

public class UrlFrontier {
    private final Queue<String> queue = new LinkedList<>();
    private final Set<String> visited = new HashSet<>();

    private static final int MAX_QUEUE = 10_000;

    public UrlFrontier(Collection<String> initialVisitedUrls) {
        if (initialVisitedUrls != null) {
            visited.addAll(initialVisitedUrls);
        }
    }

    public boolean add(String url) {
        if (url != null && !visited.contains(url) && !queue.contains(url)
                && queue.size() < MAX_QUEUE) {
            queue.offer(url);
            return true;
        }
        return false;
    }

    public String poll() {
        String url = queue.poll();
        if (url != null) visited.add(url);
        return url;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int visitedCount() {
        return visited.size();
    }

    public int queueSize() {
        return queue.size();
    }
}