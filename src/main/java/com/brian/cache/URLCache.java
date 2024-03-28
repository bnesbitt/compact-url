package com.brian.cache;

import java.util.UUID;

/**
 * A facade around a URL cache.
 * The implementation could be a persistent store (DB), or an in-memory store.
 *
 * For this example we just implement an im-memory store.
 */
public interface URLCache {

    String shorten(UUID uuid, String url);

    String getOriginalUrlFor(String hash);
}
