package com.brian.cache;

import com.brian.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An in-memory data store to map URLs to the shortened counterparts.
 *
 */
public class InMemoryURLCache implements URLCache, Runnable, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryURLCache.class);

    // The key is the full URL (lowercase).
    // The value is a URLEntry.
    private final Map<String, URLEntry> cache = new HashMap<>();

    // Contains the hashes used to generate the short URLs.
    // The key is the hash, and the value is the original URL.
    private final Map<String, String> hashes = new HashMap<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final String domain;

    // The cache TTL in milliseconds.
    private final int ttl;

    private final ScheduledExecutorService scheduler;

    private final URLEncoder encoder;

    public InMemoryURLCache(URLEncoder encoder, String domain, int ttl) {
        this.domain = domain;
        this.encoder = encoder;
        this.ttl = ttl;

        // Schedule a periodic task to evict old entries.
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this, 0, 5, TimeUnit.SECONDS);
    }

    public String shorten(UUID uuid, String url) {
        try {
            String lowercaseUrl = url.toLowerCase();

            // This will validate the URL.
            URI uri = new URL(lowercaseUrl).toURI();

            // Check the cache for an existing entry.
            try {
                lock.readLock().lock();
                var entry = cache.get(lowercaseUrl);
                if (entry != null) {
                    String shortUrl = entry.shortUrl();
                    logger.info("[{}] Found an existing entry for {} : {}", uuid, url, shortUrl);
                    return shortUrl;
                }
            } finally {
                lock.readLock().unlock();
            }

            try {
                lock.writeLock().lock();

                // Check the cache (again) for an existing entry.
                var entry = cache.get(lowercaseUrl);
                if (entry != null) {
                    String shortUrl = entry.shortUrl();
                    logger.info("[{}] Found an existing entry for {} : {}", uuid, url, shortUrl);
                    return shortUrl;
                }

                // Acquire a unique hash.
                var encoding = getUniqueHash(lowercaseUrl);
                String shortUrl = uri.getScheme() + "://" + domain + "/" + encoding;

                // Store the URL and its shortened version.
                cache.put(lowercaseUrl, new URLEntry(lowercaseUrl, shortUrl, encoding, System.currentTimeMillis()));

                logger.info("[{}] Caching URL {} with short version {}", uuid, url, shortUrl);

                return shortUrl;
            } finally {
                lock.writeLock().unlock();
            }

        } catch (URISyntaxException | MalformedURLException e) {
            // Not a valid URL
            return null;
        }
    }

    public String getOriginalUrlFor(String hash) {
        lock.readLock().lock();
        try {
            return hashes.get(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    private String getUniqueHash(String url) {
        var encoding = "";

        for (;;) {
            encoding = encoder.encode(url);
            if (encoding != null && hashes.get(encoding) == null) {
                // record that we have this hash.
                hashes.put(encoding, url);

                // We have a unique hash, we're done here.
                break;
            }
        }

        return encoding;
    }

    /**
     * This will scan the cache and evict old entries based on the TTL.
     */
    @Override
    public void run() {
        long now = System.currentTimeMillis();

        // Evict old entries.
        lock.writeLock().lock();

        try {
            logger.info("Scanning for expired entries. The cache currently has {} entries.", cache.size());

            for (var it = cache.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                var urlEntry = entry.getValue();
                if (urlEntry.hasExpired(now, ttl)) {
                    var hash = urlEntry.hash();
                    hashes.remove(hash);
                    logger.info("Removing URL {} from the cache as its TTL has expired", urlEntry.url());
                    it.remove();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        logger.info("Shutting down the cache");

        lock.writeLock().lock();
        try {
            scheduler.shutdown();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return cache.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
