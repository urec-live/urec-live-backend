package com.ureclive.urec_live_backend.rag;

import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class UrecLivePages {
    public record Page(String url, String title, String text, String fetchedAt) {}
    public record Fetch(Page page, boolean cached) {}
    private record Cached(Page page, Instant expires) {}
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private final ReentrantLock gate = new ReentrantLock();
    private final LiveHttp http;
    private Clock clock = Clock.systemUTC();
    private RobotsRules robots;
    private Instant robotsExpire = Instant.MIN;
    private long nextRequest;
    public UrecLivePages(LiveHttp http) { this.http = http; }
    void useClock(Clock clock) { this.clock = clock; }

    public static URI allowed(String url) {
        try {
            URI u = URI.create(url).normalize();
            if (!"https".equalsIgnoreCase(u.getScheme()) || !"urec.charlotte.edu".equalsIgnoreCase(u.getHost())
                    || u.getUserInfo() != null || u.getPort() != -1 && u.getPort() != 443
                    || u.getQuery() != null) throw new IllegalArgumentException("Outside UREC scope");
            String path = u.getPath();
            if (path == null || path.isEmpty()) path = "/";
            if (path.matches("(?i).*\\.(pdf|png|jpg|jpeg|gif|svg|zip|xml|js|css)$")
                    || path.matches("(?i).*/(admin|user|search)(/.*)?")) throw new IllegalArgumentException("Unsupported page");
            return new URI("https", "urec.charlotte.edu", path, null);
        } catch (Exception e) { throw new IllegalArgumentException("Outside UREC scope"); }
    }

    public Fetch fetch(String url, long deadline) throws Exception {
        URI uri = allowed(url);
        if (!gate.tryLock(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
            throw new java.util.concurrent.TimeoutException();
        try {
            Cached found = cache.get(uri.toString());
            if (found != null && clock.instant().isBefore(found.expires())) return new Fetch(found.page(), true);
            cache.remove(uri.toString());
            ensureRobots(deadline);
            URI current = uri;
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (!robots.allows(current.getRawPath())) throw new IllegalStateException("Robots disallow");
                pace(deadline);
                var response = http.request(current, null, Map.of("User-Agent", "URECLiveKnowledgeBot/1.0"),
                        LiveHttp.within(deadline, 6), 2_000_000);
                if (response.status() >= 300 && response.status() < 400) {
                    if (response.header("location").isBlank()) throw new IllegalStateException("Missing redirect");
                    current = allowed(current.resolve(response.header("location")).toString());
                    continue;
                }
                if (response.status() != 200 || !response.header("content-type").toLowerCase(Locale.ROOT).contains("text/html"))
                    throw new IllegalStateException("Page unavailable or unsupported");
                Page page = extract(current.toString(), response.body(), clock.instant());
                if (cache.size() >= 100) cache.remove(cache.keySet().iterator().next());
                cache.put(uri.toString(), new Cached(page, clock.instant().plusSeconds(300)));
                return new Fetch(page, false);
            }
            throw new IllegalStateException("Too many redirects");
        } finally { gate.unlock(); }
    }

    private void ensureRobots(long deadline) throws Exception {
        if (robots != null && clock.instant().isBefore(robotsExpire)) return;
        URI uri = URI.create("https://urec.charlotte.edu/robots.txt");
        for (int i = 0; i <= 3; i++) {
            pace(deadline);
            var response = http.request(uri, null, Map.of("User-Agent", "URECLiveKnowledgeBot/1.0"),
                    LiveHttp.within(deadline, 5), 512_000);
            if (response.status() >= 300 && response.status() < 400) {
                if (response.header("location").isBlank()) throw new IllegalStateException("Missing robots redirect");
                uri = allowed(uri.resolve(response.header("location")).toString());
                continue;
            }
            if (response.status() == 404) robots = new RobotsRules("");
            else if (response.status() == 200) robots = new RobotsRules(response.body());
            else throw new IllegalStateException("Robots unavailable");
            nextRequest = Math.max(nextRequest, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(robots.delayMillis));
            robotsExpire = clock.instant().plusSeconds(300);
            return;
        }
        throw new IllegalStateException("Robots redirect limit");
    }

    private void pace(long deadline) throws Exception {
        long wait = nextRequest - System.nanoTime();
        if (System.nanoTime() + Math.max(0, wait) >= deadline) throw new java.util.concurrent.TimeoutException();
        if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
        nextRequest = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(robots == null ? 500 : robots.delayMillis);
    }

    static Page extract(String url, String html, Instant fetched) {
        var doc = Jsoup.parse(html, url);
        String title = doc.title().split("\\|", 2)[0].trim();
        var content = doc.selectFirst("article");
        if (content == null) content = doc.selectFirst("main, [role=main]");
        if (content == null) throw new IllegalStateException("No readable main content");
        content.select("script,style,nav,footer,header,form,noscript,[aria-hidden=true]").remove();
        for (var row : content.select("tr"))
            row.replaceWith(new TextNode(row.select("th,td").stream().map(org.jsoup.nodes.Element::text)
                    .collect(java.util.stream.Collectors.joining(" | ")) + "\n"));
        for (var element : content.select("h1,h2,h3,h4,p,li,div,section,br")) element.appendChild(new TextNode("\n"));
        String text = content.wholeText().replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() < 60) throw new IllegalStateException("Insufficient readable content");
        return new Page(url, title.isBlank() ? "University Recreation" : title, text, fetched.toString());
    }
}
