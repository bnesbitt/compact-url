package com.brian.cache;

public record URLEntry(String url, String shortUrl, String hash, long timeAdded) {

    public boolean hasExpired(long now, int ttl) {
        return (now - timeAdded) > (long) ttl;
    }

}
