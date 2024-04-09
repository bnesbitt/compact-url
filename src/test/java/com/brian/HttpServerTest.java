package com.brian;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;


class HttpServerTest {

    private static final class TestServerThread implements Runnable {

        private final HttpServer httpServer = new HttpServer();

        @Override
        public void run() {
            try {
                httpServer.run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
               System.out.println("Shutting down the test server.");
            }
        }

        public HttpServer server() {
            return httpServer;
        }
    }

    private static Thread testThread;
    private static TestServerThread testServer = new TestServerThread();

    @BeforeAll
    static void start() throws InterruptedException {
        testThread = new Thread(testServer);
        testThread.start();

        // Wait for server to bind to port.
        boolean serverStared = false;
        for (int i = 0; i < 10; ++i) {
            Thread.sleep(1000);
            if (testServer.server().isRunning()) {
                serverStared = true;
                break;
            }
        }

        assertTrue(serverStared, "The server failed to start!");
    }

    @AfterAll
    static void end() throws InterruptedException {
        // Wait a bit until the server is idle.
        Thread.sleep(2000);

        if (null != testThread) {
            testThread.interrupt();
        }
    }

    @Test
    void serverIsRunning() {
        assertTrue(testServer.server().isRunning());
    }

    @Test
    void shortenURL() throws URISyntaxException, IOException, InterruptedException {
        String urlHash = "";

        {
            var request = HttpRequest.newBuilder()
                    .version(Version.HTTP_1_1)
                    .uri(new URI("http://127.0.0.1:8888"))
                    .headers("Content-Type", "text/plain;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString("http://google.com/big/path/asdfasdfasdf"))
                    .timeout(Duration.of(5, ChronoUnit.SECONDS))
                    .build();

            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            var body = response.body();
            assertFalse(body.isEmpty());

            URL u = new URL(body);
            urlHash = u.getPath();
            if (!urlHash.isBlank() && urlHash.startsWith("/")) {
                urlHash = urlHash.substring(1);
            }
        }

        // Now use the new URL hash
        {
            String shortenedURL = "http://127.0.0.1:8888" + "/" + urlHash;

            var request = HttpRequest.newBuilder()
                    .version(Version.HTTP_1_1)
                    .uri(new URI(shortenedURL))
                    .GET()
                    .timeout(Duration.of(5, ChronoUnit.SECONDS))
                    .build();

            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(301, response.statusCode());
        }
    }

    @Test
    void postWithNoBody() throws URISyntaxException, IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .version(Version.HTTP_1_1)
                .uri(new URI("http://127.0.0.1:8888"))
                .headers("Content-Type", "text/plain;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .timeout(Duration.of(5, ChronoUnit.SECONDS))
                .build();

        var client = HttpClient.newHttpClient();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
    }

    @Test
    void unsupportedMethod() throws URISyntaxException, IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .version(Version.HTTP_1_1)
                .uri(new URI("http://127.0.0.1:8888"))
                .headers("Content-Type", "text/plain;charset=UTF-8")
                .DELETE()
                .timeout(Duration.of(5, ChronoUnit.SECONDS))
                .build();

        var client = HttpClient.newHttpClient();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }
}
