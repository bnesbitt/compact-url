package com.brian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryURLCache implements URLCache {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryURLCache.class);

    private final Map<String, String> cache = new HashMap<>();

    private final Set<String> hashes = new HashSet<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final String domain;

    private final URLEncoder encoder;

    private final Random rand = new SecureRandom();

    public InMemoryURLCache(URLEncoder encoder, String domain) {
        this.domain = domain;
        this.encoder = encoder;
    }

    public String shorten(String url) {
        try {
            String lowercaseUrl = url.toLowerCase();

            // This will validate the URL.
            URI uri = new URL(lowercaseUrl).toURI();

            // Check the cache for an existing entry.
            try {
                lock.readLock().lock();
                var shortUrl = cache.get(lowercaseUrl);
                if (shortUrl != null) {
                    return shortUrl;
                }
            } finally {
                lock.readLock().unlock();
            }

            try {
                lock.writeLock().lock();

                // Check the cache (again) for an existing entry.
                var shortUrl = cache.get(lowercaseUrl);
                if (shortUrl != null) {
                    return shortUrl;
                }

                // Acquire a unique hash.
                var encoding = getUniqueHash(lowercaseUrl);
                shortUrl = uri.getScheme() + "://" + domain + "/" + encoding;

                // Store the URL and its shortened version.
                cache.put(lowercaseUrl, shortUrl);

                return shortUrl;
            } finally {
                lock.writeLock().unlock();
            }

        } catch (URISyntaxException | MalformedURLException e) {
            // Not a valid URL
            return null;
        }
    }

    private String getUniqueHash(String url) {
        var encoding = "";

        for (;;) {
            encoding = encoder.encode(url);
            if (encoding != null && !hashes.contains(encoding)) {
                // record that we have this hash.
                hashes.add(encoding);

                // We have a unique hash, we're done here.
                break;
            }
        }

        return encoding;
    }

}
