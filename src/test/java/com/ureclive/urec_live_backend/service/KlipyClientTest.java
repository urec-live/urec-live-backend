package com.ureclive.urec_live_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ureclive.urec_live_backend.dto.MediaSearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KlipyClient against a local stand-in for the KLIPY API, so no network or real key is needed. */
class KlipyClientTest {

    private static final String KEY = "test-key";

    /** A KLIPY-shaped page: a GIF, an ad (to be dropped) and a GIF with only a gif rendition in hd. */
    private static final String PAGE = """
            {"result": true, "data": {"data": [
              {"id": 101, "slug": "lift-heavy", "title": "Lift", "type": "gif",
               "file": {
                 "hd": {"gif": {"url": "https://static.klipy.com/hd.gif", "width": 498, "height": 280}},
                 "md": {"gif": {"url": "https://static.klipy.com/md.gif", "width": 300, "height": 168},
                        "webp": {"url": "https://static.klipy.com/md.webp", "width": 300, "height": 168}},
                 "sm": {"webp": {"url": "https://static.klipy.com/sm.webp", "width": 220, "height": 124}},
                 "xs": {"jpg": {"url": "https://static.klipy.com/xs.jpg", "width": 90, "height": 50}}
               }},
              {"id": 5, "type": "ad", "file": {"md": {"gif": {"url": "https://ads.example.com/ad.gif"}}}},
              {"id": 102, "type": "gif",
               "file": {"hd": {"gif": {"url": "https://static.klipy.com/only-hd.gif", "width": 0}}}},
              {"id": 103, "slug": "no-files", "type": "gif", "file": {}}
            ], "current_page": 1, "per_page": 24, "has_next": true}}
            """;

    private HttpServer server;
    private final List<URI> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> bodies = new HashMap<>();
    private volatile int status = 200;
    private volatile String body = PAGE;
    private volatile CountDownLatch shares = new CountDownLatch(1);
    private MutableClock clock;
    private KlipyClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI());
            if ("POST".equals(exchange.getRequestMethod())) {
                bodies.put(exchange.getRequestURI().getPath(),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                shares.countDown();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        clock = new MutableClock(Instant.parse("2026-11-03T12:00:00Z"));
        client = clientWithKey(KEY);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private KlipyClient clientWithKey(String key) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
        return new KlipyClient(new ObjectMapper(), key, base, "pg-13", "klipy.com", "secret",
                HttpClient.newHttpClient(), clock);
    }

    @Test
    void trendingIsNormalizedWithAdsAndUnusableItemsDropped() {
        MediaSearchResponse page = client.trending(KlipyClient.Category.GIFS, 1, "alice");

        assertEquals(2, page.items.size());
        MediaSearchResponse.Item first = page.items.get(0);
        assertEquals("lift-heavy", first.id);
        // md beats hd, and webp beats gif
        assertEquals("https://static.klipy.com/md.webp", first.url);
        assertEquals("https://static.klipy.com/sm.webp", first.previewUrl);
        assertEquals(300, first.width);
        assertEquals(168, first.height);

        // No slug: falls back to the id; only an hd gif: used for both; zero width: unknown
        MediaSearchResponse.Item second = page.items.get(1);
        assertEquals("102", second.id);
        assertEquals("https://static.klipy.com/only-hd.gif", second.url);
        assertEquals("https://static.klipy.com/only-hd.gif", second.previewUrl);
        assertNull(second.width);

        assertEquals(2, page.nextPage);
    }

    @Test
    void requestsCarryTheKeyRatingPageSizeAndAHashedCustomerId() {
        client.search(KlipyClient.Category.STICKERS, "  leg day  ", 3, "alice");

        URI request = requests.get(0);
        assertEquals("/api/v1/" + KEY + "/stickers/search", request.getPath());
        Map<String, String> query = query(request);
        assertEquals("leg day", query.get("q"));
        assertEquals("3", query.get("page"));
        assertEquals(String.valueOf(KlipyClient.PER_PAGE), query.get("per_page"));
        assertEquals("pg-13", query.get("rating"));
        assertEquals(client.customerId("alice"), query.get("customer_id"));
        assertFalse(request.toString().contains("alice"));
    }

    @Test
    void longQueriesAreCutAndBlankOnesMeanTrending() {
        client.search(KlipyClient.Category.GIFS, "x".repeat(80), 1, "alice");
        assertEquals(KlipyClient.MAX_QUERY_LENGTH, query(requests.get(0)).get("q").length());

        client.search(KlipyClient.Category.GIFS, "   ", 1, "alice");
        assertEquals("/api/v1/" + KEY + "/gifs/trending", requests.get(1).getPath());
    }

    @Test
    void lastPageHasNoNextPage() {
        body = PAGE.replace("\"has_next\": true", "\"has_next\": false");
        assertNull(client.search(KlipyClient.Category.GIFS, "squat", 1, "alice").nextPage);
    }

    @Test
    void trendingIsCachedForFiveMinutes() {
        client.trending(KlipyClient.Category.GIFS, 1, "alice");
        client.trending(KlipyClient.Category.GIFS, 1, "bob");
        assertEquals(1, requests.size());

        // Different page or category is a different cache entry
        client.trending(KlipyClient.Category.GIFS, 2, "alice");
        client.trending(KlipyClient.Category.STICKERS, 1, "alice");
        assertEquals(3, requests.size());

        clock.advanceMillis(KlipyClient.TRENDING_TTL.toMillis() + 1);
        client.trending(KlipyClient.Category.GIFS, 1, "alice");
        assertEquals(4, requests.size());
    }

    @Test
    void searchIsNeverCached() {
        client.search(KlipyClient.Category.GIFS, "bench", 1, "alice");
        client.search(KlipyClient.Category.GIFS, "bench", 1, "alice");
        assertEquals(2, requests.size());
    }

    @Test
    void errorsBecomeClearStatuses() {
        status = 429;
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> client.search(KlipyClient.Category.GIFS, "a", 1, "u"));
        status = 401;
        assertStatus(HttpStatus.BAD_GATEWAY, () -> client.search(KlipyClient.Category.GIFS, "a", 1, "u"));
        status = 500;
        assertStatus(HttpStatus.BAD_GATEWAY, () -> client.search(KlipyClient.Category.GIFS, "a", 1, "u"));
        status = 200;
        body = "{\"result\": false, \"errors\": {\"message\": \"bad\"}}";
        assertStatus(HttpStatus.BAD_GATEWAY, () -> client.search(KlipyClient.Category.GIFS, "a", 1, "u"));
        body = "not json";
        assertStatus(HttpStatus.BAD_GATEWAY, () -> client.search(KlipyClient.Category.GIFS, "a", 1, "u"));
    }

    @Test
    void unreachableServiceIsABadGateway() {
        server.stop(0);
        assertStatus(HttpStatus.BAD_GATEWAY, () -> client.search(KlipyClient.Category.GIFS, "a", 1, "u"));
    }

    @Test
    void withoutAKeyNothingIsCalled() {
        KlipyClient unconfigured = clientWithKey("  ");
        assertFalse(unconfigured.isConfigured());
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> unconfigured.trending(KlipyClient.Category.GIFS, 1, "u"));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> unconfigured.search(KlipyClient.Category.GIFS, "a", 1, "u"));
        unconfigured.reportShare(KlipyClient.Category.GIFS, "slug", "u");
        assertTrue(requests.isEmpty());
    }

    @Test
    void onlyHttpsKlipyHostsAreAllowed() {
        assertTrue(client.isAllowedMediaUrl("https://static.klipy.com/ii/a/md.webp"));
        assertTrue(client.isAllowedMediaUrl("https://klipy.com/a.gif"));
        assertTrue(client.isAllowedMediaUrl("https://MEDIA.KLIPY.COM/a.gif"));
        assertFalse(client.isAllowedMediaUrl("http://static.klipy.com/a.gif"));
        assertFalse(client.isAllowedMediaUrl("https://notklipy.com/a.gif"));
        assertFalse(client.isAllowedMediaUrl("https://klipy.com.evil.io/a.gif"));
        assertFalse(client.isAllowedMediaUrl("https://evil.io/?u=klipy.com"));
        assertFalse(client.isAllowedMediaUrl("ftp://klipy.com/a.gif"));
        assertFalse(client.isAllowedMediaUrl("not a url"));
        assertFalse(client.isAllowedMediaUrl(""));
        assertFalse(client.isAllowedMediaUrl(null));
    }

    @Test
    void customerIdIsStablePerUserAndHidesTheUsername() {
        assertEquals(client.customerId("alice"), client.customerId("alice"));
        assertNotEquals(client.customerId("alice"), client.customerId("bob"));
        assertEquals(32, client.customerId("alice").length());
        assertFalse(client.customerId("alice").contains("alice"));
    }

    @Test
    void shareTriggerIsPostedInTheBackground() throws Exception {
        client.reportShare(KlipyClient.Category.STICKERS, "flex", "alice");
        assertTrue(shares.await(5, TimeUnit.SECONDS));
        String path = "/api/v1/" + KEY + "/stickers/share/flex";
        assertTrue(bodies.containsKey(path), "posted to " + bodies.keySet());
        assertTrue(bodies.get(path).contains(client.customerId("alice")));
    }

    @Test
    void shareTriggerSkipsItemsWithoutASlug() {
        client.reportShare(KlipyClient.Category.GIFS, "  ", "alice");
        client.reportShare(KlipyClient.Category.GIFS, null, "alice");
        assertTrue(requests.isEmpty());
    }

    @Test
    void categoriesComeFromUrlSegments() {
        assertEquals(KlipyClient.Category.GIFS, KlipyClient.Category.fromPath("gifs"));
        assertEquals(KlipyClient.Category.STICKERS, KlipyClient.Category.fromPath("Stickers"));
        assertStatus(HttpStatus.NOT_FOUND, () -> KlipyClient.Category.fromPath("clips"));
    }

    private static void assertStatus(HttpStatus expected, Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(expected, e.getStatusCode());
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        return params;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
