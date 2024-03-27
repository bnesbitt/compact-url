package com.brian;

public interface URLEncoder {

    /**
     * Encodes a URL.
     *
     * @param url The URL to be encoded.
     * @return The encoded URL, or null if the URL cannot be encoded.
     */
    String encode(String url);
}

