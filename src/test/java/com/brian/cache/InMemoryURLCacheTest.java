package com.brian.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.brian.Base62Encoder;
import com.brian.URLEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class InMemoryURLCacheTest {

    private final UUID uuid = UUID.randomUUID();

    @Test
    void invalidURL() {
        URLEncoder encoder = Mockito.mock(URLEncoder.class);
        try (var cache = new InMemoryURLCache(encoder, "domain", 1234)) {
            assertNull(cache.shorten(uuid, "not a url"));
            assertTrue(cache.isEmpty());
        }
    }

    @Test
    void validURL() {
        URLEncoder encoder = Mockito.mock(URLEncoder.class);
        when(encoder.encode(anyString())).thenReturn("abcd");

        try (var cache = new InMemoryURLCache(encoder, "domain", 1234)) {
            var shortUrl = cache.shorten(uuid, "http://google.com/path/foo/bar?key=value");
            assertNotNull(shortUrl);
            assertEquals("http://domain/abcd", shortUrl);
            assertFalse(cache.isEmpty());
            assertEquals(1, cache.size());
        }

        verify(encoder, times(1)).encode(anyString());
    }

    @Test
    void requestSameUrlManyTimes() {
        URLEncoder encoder = Mockito.mock(URLEncoder.class);
        when(encoder.encode(anyString())).thenReturn("abcd");

        int numRequests = 100;

        try (var cache = new InMemoryURLCache(encoder, "domain", 1234)) {
            for (int i = 0; i < numRequests; ++i) {
                var shortUrl = cache.shorten(uuid, "http://google.com/path/foo/bar?key=value");
                assertNotNull(shortUrl);
                assertEquals("http://domain/abcd", shortUrl);
                assertFalse(cache.isEmpty());
                assertEquals(1, cache.size());
            }
        }

        verify(encoder, times(1)).encode(anyString());
    }

    @Test
    void addManyEntries() {
        URLEncoder encoder = new Base62Encoder();

        int numRequests = 100;

        try (var cache = new InMemoryURLCache(encoder, "domain", 1234)) {
            for (int i = 0; i < numRequests; ++i) {
                var shortUrl = cache.shorten(uuid, "http://google.com/path/foo/bar?key=value" + i);
                assertNotNull(shortUrl);
            }

            assertEquals(numRequests, cache.size());
        }
    }

}
