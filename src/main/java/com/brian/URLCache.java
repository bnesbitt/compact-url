package com.brian;

import org.apache.commons.validator.routines.UrlValidator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class URLCache {
    private static final UrlValidator URL_VALIDATOR = new UrlValidator();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final String domain;

    public URLCache(String domain) {
        this.domain = domain;
    }

    String add(String url) {
        try {
            String lowercase_url = url.toLowerCase();
            URI uri = new URI(lowercase_url);
            String schema = uri.getScheme();
            int port = uri.getPort();

            String path = uri.getPath();
            String query = uri.getQuery();


            System.err.println("XXX: path = " + path);
            System.err.println("XXX: query = " + query);


            // TODO shorten this bit!
            String path_and_query = path + "?" + query;
            System.err.println("XXX: path & query = " + path_and_query);





            //cache.putIfAbsent(lowercase_url, );
        } catch (URISyntaxException e) {
            // Not a valid URL
            return null;
        }

        return null;
    }

}
