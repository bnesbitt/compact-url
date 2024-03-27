package com.brian.cache;

public record URLEntry(String url, String shortUrl, String hash, long timeAdded) {

    /**
     * Determines if a cache entry has expired. Used to evict entries from the cache.
     *
     * @param now The current epoch time in milliseconds.
     * @param ttl The TTL in milliseconds.
     *
     * @return true if the entry has expired.
     */
    public boolean hasExpired(long now, int ttl) {
        return (now - timeAdded) > (long) ttl;
    }

}
