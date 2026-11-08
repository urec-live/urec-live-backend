package com.ureclive.urec_live_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.MediaSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to the KLIPY GIF and sticker API (https://docs.klipy.com) for the app's picker. The API key
 * stays on the server; the app only sees normalized results. Ads that KLIPY mixes into results are
 * dropped, and each user is identified to KLIPY by an opaque hash, never their username.
 */
@Service
public class KlipyClient {
    private static final Logger logger = LoggerFactory.getLogger(KlipyClient.class);

    /** What the picker can browse, as KLIPY's URL segment. */
    public enum Category {
        GIFS("gifs"),
        STICKERS("stickers");

        private final String path;

        Category(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }

        public static Category fromPath(String path) {
            for (Category category : values()) {
                if (category.path.equalsIgnoreCase(path)) return category;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown media category: " + path);
        }
    }

    static final int PER_PAGE = 24;
    static final int MAX_QUERY_LENGTH = 50;
    static final Duration TRENDING_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHED_PAGES = 40;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Sizes to try for the chat rendition and the picker thumbnail, best first. */
    private static final List<String> MAIN_SIZES = List.of("md", "hd", "sm");
    private static final List<String> PREVIEW_SIZES = List.of("sm", "xs", "md");
    /** Animated formats expo-image plays on every platform; webp is smaller and keeps sticker transparency. */
    private static final List<String> FORMATS = List.of("webp", "gif");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final String apiKey;
    private final String baseUrl;
    private final String rating;
    private final Set<String> mediaHosts;
    private final byte[] customerIdKey;

    private record CachedPage(long expiresAtMs, MediaSearchResponse page) {}

    private final Map<String, CachedPage> trendingCache = new ConcurrentHashMap<>();
    private final Set<String> warnedHosts = ConcurrentHashMap.newKeySet();

    @Autowired
    public KlipyClient(
            ObjectMapper objectMapper,
            @Value("${klipy.api-key:}") String apiKey,
            @Value("${klipy.base-url:https://api.klipy.com/api/v1}") String baseUrl,
            @Value("${klipy.rating:pg-13}") String rating,
            @Value("${klipy.media-hosts:klipy.com}") String mediaHosts,
            @Value("${jwt.secret}") String customerIdSecret) {
        this(objectMapper, apiKey, baseUrl, rating, mediaHosts, customerIdSecret,
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), Clock.systemUTC());
    }

    KlipyClient(ObjectMapper objectMapper, String apiKey, String baseUrl, String rating, String mediaHosts,
                String customerIdSecret, HttpClient httpClient, Clock clock) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.rating = rating;
        this.mediaHosts = Set.copyOf(Arrays.stream(mediaHosts.split(","))
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty())
                .toList());
        this.customerIdKey = customerIdSecret.getBytes(StandardCharsets.UTF_8);
        this.httpClient = httpClient;
        this.clock = clock;
    }

    /** False until KLIPY_API_KEY is set; the picker endpoints then answer 503. */
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /** Trending items, cached for a few minutes since every user sees the same list. */
    public MediaSearchResponse trending(Category category, int page, String username) {
        requireConfigured();
        int safePage = Math.max(page, 1);
        String cacheKey = category.path() + ":" + safePage;
        long now = clock.millis();
        CachedPage cached = trendingCache.get(cacheKey);
        if (cached != null && cached.expiresAtMs() > now) return cached.page();

        MediaSearchResponse result = fetch(category, "trending", null, safePage, username);
        if (trendingCache.size() >= MAX_CACHED_PAGES) trendingCache.clear();
        trendingCache.put(cacheKey, new CachedPage(now + TRENDING_TTL.toMillis(), result));
        return result;
    }

    /** Search results; a blank query falls back to trending. */
    public MediaSearchResponse search(Category category, String query, int page, String username) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) return trending(category, page, username);
        requireConfigured();
        if (q.length() > MAX_QUERY_LENGTH) q = q.substring(0, MAX_QUERY_LENGTH);
        return fetch(category, "search", q, Math.max(page, 1), username);
    }

    /**
     * True if the URL is https on a KLIPY host (klipy.media-hosts, subdomains included). Chat sends are
     * checked against this so members can't post arbitrary image links as "GIFs".
     */
    public boolean isAllowedMediaUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return mediaHosts.stream().anyMatch(allowed -> h.equals(allowed) || h.endsWith("." + allowed));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Tells KLIPY an item was sent, which feeds its trending lists. Best effort: runs in the
     * background, and failures are only logged at debug level.
     */
    public void reportShare(Category category, String slug, String username) {
        if (!isConfigured() || slug == null || slug.isBlank()) return;
        try {
            String body = objectMapper.writeValueAsString(Map.of("customer_id", customerId(username)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            baseUrl + "/" + encode(apiKey) + "/" + category.path() + "/share/" + encode(slug.trim())))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            logger.debug("[KLIPY] share trigger failed: {}", error.toString());
                        } else if (response.statusCode() >= 300) {
                            logger.debug("[KLIPY] share trigger answered HTTP {}", response.statusCode());
                        }
                    });
        } catch (IOException | RuntimeException e) {
            logger.debug("[KLIPY] share trigger not sent: {}", e.toString());
        }
    }

    /** A stable per-user id for KLIPY that can't be turned back into the username. */
    String customerId(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(customerIdKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(("klipy:" + username).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GIF search isn't set up on this server");
        }
    }

    private MediaSearchResponse fetch(Category category, String endpoint, String query, int page, String username) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append('/').append(encode(apiKey))
                .append('/').append(category.path())
                .append('/').append(endpoint)
                .append("?page=").append(page)
                .append("&per_page=").append(PER_PAGE)
                .append("&rating=").append(encode(rating))
                .append("&customer_id=").append(customerId(username));
        if (query != null) url.append("&q=").append(encode(query));

        // Never log the URL itself: the API key is part of the path
        String what = category.path() + "/" + endpoint;
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unreachable(what, e);
        } catch (IOException e) {
            throw unreachable(what, e);
        }

        int status = response.statusCode();
        if (status == 429) {
            logger.warn("[KLIPY] {} rate limited (HTTP 429)", what);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "GIF search is busy. Try again in a minute.");
        }
        if (status < 200 || status >= 300) {
            logger.error("[KLIPY] {} answered HTTP {}{}", what, status,
                    status == 401 || status == 403 ? " (check KLIPY_API_KEY)" : "");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GIF search failed");
        }
        return parse(what, response.body(), page);
    }

    private ResponseStatusException unreachable(String what, Exception e) {
        logger.warn("[KLIPY] {} unreachable: {}", what, e.toString());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't reach GIF search");
    }

    MediaSearchResponse parse(String what, String body, int page) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            logger.error("[KLIPY] {} returned invalid JSON", what);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GIF search failed");
        }
        if (!root.path("result").asBoolean(false)) {
            logger.error("[KLIPY] {} returned result=false: {}", what, root.path("errors"));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GIF search failed");
        }
        JsonNode data = root.path("data");
        List<MediaSearchResponse.Item> items = new ArrayList<>();
        for (JsonNode item : data.path("data")) {
            if ("ad".equalsIgnoreCase(item.path("type").asText())) continue;
            MediaSearchResponse.Item parsed = toItem(item);
            if (parsed != null) items.add(parsed);
        }
        Integer nextPage = data.path("has_next").asBoolean(false) ? page + 1 : null;
        return new MediaSearchResponse(items, nextPage);
    }

    private MediaSearchResponse.Item toItem(JsonNode item) {
        JsonNode file = item.path("file");
        JsonNode main = pick(file, MAIN_SIZES);
        if (main == null) return null;
        JsonNode preview = pick(file, PREVIEW_SIZES);
        String slug = item.hasNonNull("slug") ? item.get("slug").asText() : item.path("id").asText();
        String url = main.path("url").asText();
        warnIfHostNotAllowed(url);
        return new MediaSearchResponse.Item(
                slug,
                url,
                preview == null ? url : preview.path("url").asText(),
                positiveOrNull(main.path("width")),
                positiveOrNull(main.path("height")));
    }

    /** The first size that has an animated format with a URL. */
    private static JsonNode pick(JsonNode file, List<String> sizes) {
        for (String size : sizes) {
            for (String format : FORMATS) {
                JsonNode rendition = file.path(size).path(format);
                if (!rendition.path("url").asText("").isBlank()) return rendition;
            }
        }
        return null;
    }

    private static Integer positiveOrNull(JsonNode node) {
        int value = node.asInt(0);
        return value > 0 ? value : null;
    }

    private void warnIfHostNotAllowed(String url) {
        if (isAllowedMediaUrl(url)) return;
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            host = url;
        }
        if (warnedHosts.add(String.valueOf(host))) {
            logger.warn("[KLIPY] media host {} isn't in klipy.media-hosts {}; sending these GIFs will be rejected",
                    host, mediaHosts);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
