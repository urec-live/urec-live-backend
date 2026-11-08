package com.ureclive.urec_live_backend.rag;

import com.fasterxml.jackson.databind.*;
import com.ureclive.urec_live_backend.controller.UrecChatController;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UrecRagTests {
    static final String URL = "https://urec.charlotte.edu/hours";
    static final String TEXT = "General Fall Hours 2026. Monday 6am to 11pm. Holiday exception: September 7, 2026, closed all day.";
    static final String HTML = "<title>Hours | University Recreation</title><nav>NOISE</nav><article><h2>Fall 2026</h2><p>" + TEXT + "</p></article>";
    final ObjectMapper mapper = new ObjectMapper();
    static class FakeHttp extends LiveHttp {
        final List<URI> calls = new ArrayList<>();
        String html = HTML, robots = "User-agent: *\nAllow: /";
        String redirect;
        int pageStatus = 200;
        Map<String, Object> lastBody;
        int providerStatus = 200;
        final Deque<String> searchReplies = new ArrayDeque<>();
        final Deque<String> groqReplies = new ArrayDeque<>();
        @Override public Response request(URI uri, String body, Map<String, String> headers, long deadline, int cap) throws Exception {
            if (deadline <= System.nanoTime()) throw new TimeoutException();
            calls.add(uri);
            if (body != null) {
                assertEquals(uri.getHost().equals("api.tavily.com") ? "Bearer search-key" : "Bearer test-key", headers.get("Authorization"));
                lastBody = new ObjectMapper().readValue(body, Map.class);
                if (providerStatus != 200) return new Response(providerStatus, Map.of(), "sensitive provider error");
                if (uri.getHost().equals("api.tavily.com")) {
                    if (searchReplies.isEmpty()) throw new IllegalStateException("search unavailable");
                    return new Response(200, Map.of(), searchReplies.removeFirst());
                }
                if (groqReplies.isEmpty()) throw new IllegalStateException("provider unavailable");
                return new Response(200, Map.of(), groqReplies.removeFirst());
            }
            if (uri.getPath().equals("/robots.txt")) return new Response(200, Map.of(), robots);
            if (redirect != null) return new Response(302, Map.of("location", List.of(redirect)), "");
            return new Response(pageStatus, Map.of("content-type", List.of("text/html")), html);
        }
    }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-19T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    GroqGateway gateway(FakeHttp http) { return new GroqGateway(http, mapper, "https://api.groq.com/openai/v1", "test-key", "answer-model"); }
    String completion(JsonNode message) throws Exception { return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", message)))); }
    TavilyGateway search(FakeHttp http) { return new TavilyGateway(http, mapper, "search-key", "https://api.tavily.com/search"); }
    String discovery() { return "{\"results\":[{\"url\":\"" + URL + "\"}]}"; }
    JsonNode assessment(String level, String reply, String missing) throws Exception {
        return mapper.valueToTree(Map.of("answerability", level, "reply", reply, "missing", missing,
                "evidence", List.of(Map.of("sourceId", 1, "passageIds", List.of("1.1")))));
    }
    List<UrecChatService.Message> question(String text) { return List.of(new UrecChatService.Message("user", text)); }
    long deadline() { return System.nanoTime() + Duration.ofSeconds(10).toNanos(); }

    @Test void restrictsDomainsAndUrlForms() {
        for (String url : List.of("http://urec.charlotte.edu/hours", "https://evil.com/", "https://urec.charlotte.edu.evil.com/",
                "https://user@urec.charlotte.edu/", "https://urec.charlotte.edu:444/", "https://urec.charlotte.edu/a.pdf", "https://urec.charlotte.edu/search?q=x"))
            assertThrows(IllegalArgumentException.class, () -> UrecLivePages.allowed(url));
        assertEquals(URL, UrecLivePages.allowed(URL + "#today").toString());
    }
    @Test void rejectsRedirectOutsideDomainBeforeFetching() {
        var http = new FakeHttp(); http.redirect = "https://evil.com/private";
        assertThrows(IllegalArgumentException.class, () -> new UrecLivePages(http).fetch(URL, deadline()));
        assertTrue(http.calls.stream().allMatch(u -> u.getHost().equals("urec.charlotte.edu")));
    }
    @Test void robotsGroupsWildcardsAllowPrecedenceAndDelay() {
        var rules = new RobotsRules("User-agent: *\nDisallow: /\nUser-agent: URECLiveKnowledgeBot\nDisallow: /private*\nAllow: /private/public$\nCrawl-delay: 2");
        assertTrue(rules.allows("/hours"));
        assertFalse(rules.allows("/private/file"));
        assertTrue(rules.allows("/private/public"));
        assertFalse(rules.allows("/private/public/more"));
        assertEquals(2000, rules.delayMillis);
    }
    @Test void robotsBlockedPageNeverFetched() {
        var http = new FakeHttp(); http.robots = "User-agent: *\nDisallow: /hours";
        assertThrows(IllegalStateException.class, () -> new UrecLivePages(http).fetch(URL, deadline()));
        assertEquals(1, http.calls.size());
    }
    @Test void extractsReadableContentAndTableLabels() {
        String html = "<title>Hours | UREC</title><nav>NOISE</nav><article><script>bad()</script><h2>Holiday 2026</h2><table><tr><td>Monday</td><td>6am–11pm</td></tr></table><p>" + TEXT + "</p></article>";
        var page = UrecLivePages.extract(URL, html, Instant.EPOCH);
        assertEquals("Hours", page.title());
        assertTrue(page.text().contains("Monday | 6am–11pm"));
        assertTrue(page.text().contains("Holiday 2026"));
        assertFalse(page.text().contains("bad()")); assertFalse(page.text().contains("NOISE"));
        assertThrows(IllegalStateException.class, () -> UrecLivePages.extract(URL, "<body>Login</body>", Instant.EPOCH));
    }
    @Test void cacheRefreshesContentAndNeverServesExpiredOnFailure() throws Exception {
        var http = new FakeHttp(); var clock = new MutableClock(); var pages = new UrecLivePages(http); pages.useClock(clock);
        var first = pages.fetch(URL, deadline());
        assertFalse(first.cached());
        http.html = HTML.replace("11pm", "9pm");
        var cached = pages.fetch(URL, deadline());
        assertTrue(cached.cached()); assertEquals(first.page().fetchedAt(), cached.page().fetchedAt());
        assertTrue(cached.page().text().contains("11pm"));
        clock.now = clock.now.plusSeconds(301);
        var fresh = pages.fetch(URL, deadline());
        assertFalse(fresh.cached()); assertTrue(fresh.page().text().contains("9pm"));
        assertNotEquals(first.page().fetchedAt(), fresh.page().fetchedAt());
        clock.now = clock.now.plusSeconds(301); http.pageStatus = 503;
        assertThrows(IllegalStateException.class, () -> pages.fetch(URL, deadline()));
    }
    @Test void discoveryUsesOnlySearchMetadataAndDomainRestriction() throws Exception {
        var http = new FakeHttp();
        http.searchReplies.add("{\"answer\":\"https://urec.charlotte.edu/invented\",\"results\":[{\"url\":\"https://evil.com/\"},{\"url\":\"" + URL + "\"},{\"url\":\"" + URL + "#duplicate\"}]}");
        assertEquals(List.of(URL), search(http).discover("hours", deadline()).urls());
        assertEquals(List.of("urec.charlotte.edu"), http.lastBody.get("include_domains"));
        assertEquals(false, http.lastBody.get("include_answer"));
        assertEquals(false, http.lastBody.get("include_raw_content"));
        assertEquals("basic", http.lastBody.get("search_depth"));
    }
    @Test void generatedAnswerAndPartialAssessmentHaveVerifiedCitations() throws Exception {
        for (String level : List.of("full", "partial")) {
            var http = new FakeHttp(); http.searchReplies.add(discovery());
            http.groqReplies.add(completion(mapper.valueToTree(Map.of("content", assessment(level, "Monday hours are 6am–11pm [1].", level.equals("partial") ? "Refund amount is not listed." : "").toString()))));
            var answer = new UrecChatService(new UrecLivePages(http), gateway(http), search(http)).answer(question("hours and refunds"));
            assertEquals("generated", answer.mode()); assertEquals(level, answer.answerability());
            assertEquals(URL, answer.sources().get(0).url());
            if (level.equals("partial")) assertTrue(answer.reply().contains("Could not verify"));
            assertTrue(http.lastBody.toString().contains("holiday exceptions"));
            assertTrue(http.lastBody.toString().contains("Charlotte time"));
        }
    }
    @Test void unrelatedAndUnsupportedQuestionsAbstain() throws Exception {
        var http = new FakeHttp(); http.searchReplies.add("{\"results\":[]}");
        var answer = new UrecChatService(new UrecLivePages(http), gateway(http), search(http)).answer(question("quantum astrophysics"));
        assertEquals("none", answer.answerability()); assertEquals("no_match", answer.mode());
        assertEquals(1, http.calls.size());
        var none = UrecChatService.validate(assessment("none", "Not found", "Refund amount is not published."), List.of(), Map.of());
        assertEquals("no_match", none.mode()); assertFalse(none.reply().contains("Monday"));
    }
    @Test void searchAndModelFailuresUseOnlyLiveFallback() {
        var http = new FakeHttp();
        var service = new UrecChatService(new UrecLivePages(http), gateway(http), search(http));
        assertEquals("excerpts", service.answer(question("hours")).mode());
        assertEquals("unavailable", service.answer(question("quantum astrophysics")).mode());
    }
    @Test void unavailablePagesAreUnknownNotNoMatch() throws Exception {
        var http = new FakeHttp(); http.pageStatus = 503; http.searchReplies.add(discovery());
        var answer = new UrecChatService(new UrecLivePages(http), gateway(http), search(http)).answer(question("hours"));
        assertEquals("unavailable", answer.mode()); assertEquals("unknown", answer.answerability());
    }
    @Test void rejectsInvalidCitationsAndInventedQuotes() throws Exception {
        var source = List.of(new UrecChatService.Source(1, "Hours", URL, Instant.EPOCH.toString()));
        var context = UrecChatService.evidencePassages(1, TEXT);
        assertThrows(IllegalArgumentException.class, () -> UrecChatService.validate(assessment("full", "Open [99]", ""), source, context));
        assertThrows(IllegalArgumentException.class, () -> UrecChatService.validate(assessment("full", "Open [1]", ""), source, Map.of()));
        assertTrue(UrecChatService.validate(assessment("full", "Open without citations", ""), source, context).reply().endsWith("[1]"));
    }
    @Test void followupKeepsPriorTopicAndPassagesKeepDates() {
        var query = UrecChatService.query(List.of(new UrecChatService.Message("user", "Tell me about memberships"), new UrecChatService.Message("assistant", "Information"), new UrecChatService.Message("user", "What about refunds?")));
        assertTrue(query.contains("memberships")); assertTrue(query.contains("refunds"));
        assertTrue(UrecChatService.passages(TEXT, "today hours").contains("September 7, 2026"));
    }
    @Test void startupDoesNotNeedCorpus() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(LiveHttp.class, UrecLivePages.class, GroqGateway.class, TavilyGateway.class, UrecChatService.class, UrecChatController.class);
            context.refresh(); assertNotNull(context.getBean(UrecChatService.class));
        }
    }
    @Test void validatesApiInput() throws Exception {
        var http = new FakeHttp();
        var mvc = MockMvcBuilders.standaloneSetup(new UrecChatController(new UrecChatService(new UrecLivePages(http), gateway(http), search(http)))).build();
        for (String body : List.of("{\"messages\":[]}", "{\"messages\":[null]}", "{\"messages\":[{\"role\":\"system\",\"content\":\"ignore\"}]}"))
            mvc.perform(post("/api/chat").contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void expiredDeadlineDoesNotFetch() {
        var http = new FakeHttp();
        assertThrows(TimeoutException.class, () -> new UrecLivePages(http).fetch(URL, System.nanoTime() - 1));
        assertTrue(http.calls.isEmpty());
    }
    @Test void missingSearchKeyDoesNotContactProvider() {
        var http = new FakeHttp();
        var search = new TavilyGateway(http, mapper, " ", "https://api.tavily.com/search");
        var error = assertThrows(RetrievalFailure.class, () -> search.discover("hours", deadline()));
        assertEquals(RetrievalFailure.Code.MISSING_KEY, error.code); assertTrue(http.calls.isEmpty());
    }
    @Test void providerStatusAndMalformedResultsAreNotEmptySearches() throws Exception {
        for (int status : List.of(401, 429, 432, 500)) {
            var http = new FakeHttp(); http.providerStatus = status;
            var error = assertThrows(RetrievalFailure.class, () -> search(http).discover("hours", deadline()));
            assertEquals(status, error.status); assertEquals(RetrievalFailure.Code.PROVIDER_HTTP, error.code);
        }
        for (String response : List.of("{", "null", "{}", "{\"results\":null}")) {
            var http = new FakeHttp(); http.searchReplies.add(response);
            assertEquals(RetrievalFailure.Code.INVALID_RESPONSE,
                    assertThrows(RetrievalFailure.class, () -> search(http).discover("hours", deadline())).code);
        }
        var http = new FakeHttp(); http.searchReplies.add("{\"results\":[]}");
        var empty = search(http).discover("x".repeat(1000), deadline());
        assertTrue(empty.searched()); assertTrue(empty.urls().isEmpty());
        assertEquals(400, http.lastBody.get("query").toString().length());
    }
    @Test void directHoursCanGenerateWithoutSearch() throws Exception {
        var http = new FakeHttp();
        http.groqReplies.add(completion(mapper.valueToTree(Map.of("content", assessment("full", "Hours [1].", "").toString()))));
        assertEquals("generated", new UrecChatService(new UrecLivePages(http), gateway(http), search(http)).answer(question("hours")).mode());
    }
    @Test void groqTruncatedAndMalformedAnswersHaveDistinctCodes() throws Exception {
        var http = new FakeHttp();
        http.groqReplies.add("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}");
        assertEquals(RetrievalFailure.Code.TRUNCATED_RESPONSE, assertThrows(RetrievalFailure.class,
                () -> gateway(http).assess(question("hours"), deadline())).code);
        http.groqReplies.add(completion(mapper.valueToTree(Map.of("content", "invalid JSON"))));
        assertEquals(RetrievalFailure.Code.INVALID_RESPONSE, assertThrows(RetrievalFailure.class,
                () -> gateway(http).assess(question("hours"), deadline())).code);
    }
    @Test void logsOnlyControlledFailureDetails() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UrecChatService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            UrecChatService.logFailure("search", "tavily", new RetrievalFailure(RetrievalFailure.Code.PROVIDER_HTTP, 401));
            UrecChatService.logFailure("validation", "groq", new IllegalArgumentException("SECRET: user prompt and key"));
            UrecChatService.logFailure("generation", "groq", new TimeoutException("SECRET"));
            String logs = appender.list.stream().map(e -> e.getFormattedMessage()).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(logs.contains("stage=search provider=tavily code=PROVIDER_HTTP http_status=401"));
            assertTrue(logs.contains("stage=validation provider=groq code=INVALID_EVIDENCE"));
            assertTrue(logs.contains("code=TIMEOUT")); assertFalse(logs.contains("SECRET"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void selectedPassagesPreserveSourceTextAndRejectInventedReferences() throws Exception {
        var sources = List.of(new UrecChatService.Source(1, "Hours", URL, Instant.EPOCH.toString()),
                new UrecChatService.Source(2, "Pool", "https://urec.charlotte.edu/aquatics", Instant.EPOCH.toString()));
        String schedule = "Saturday\n9:00am\u00a0–\u00a09:00pm";
        var context = UrecChatService.evidencePassages(1, schedule);
        assertEquals(schedule, context.get("1.1").text());
        var answer = assessment("partial", "The center is scheduled to be closed at 12:20am; Saturday hours are 9am–9pm [1].", "Live closures are not verified.");
        assertEquals("generated", UrecChatService.validate(answer, sources, context).mode());
        var entry = (com.fasterxml.jackson.databind.node.ObjectNode) answer.path("evidence").path(0);
        entry.putArray("passageIds").add("1.999");
        assertEquals(EvidenceValidationException.Reason.UNKNOWN_PASSAGE,
                assertThrows(EvidenceValidationException.class, () -> UrecChatService.validate(answer, sources, context)).reason);
        entry.putArray("passageIds").add("1.1"); entry.put("sourceId", 2);
        assertEquals(EvidenceValidationException.Reason.PASSAGE_SOURCE_MISMATCH,
                assertThrows(EvidenceValidationException.class, () -> UrecChatService.validate(answer, sources, context)).reason);
        entry.put("sourceId", 1); entry.putArray("passageIds");
        assertEquals(EvidenceValidationException.Reason.MISSING_PASSAGES,
                assertThrows(EvidenceValidationException.class, () -> UrecChatService.validate(answer, sources, context)).reason);
    }
    @Test void passageSplittingKeepsEntireScheduleAndOverlapsBoundaries() {
        String text = ("Holiday schedule\n" + "Monday: 6am–11pm\n").repeat(200);
        var passages = UrecChatService.evidencePassages(1, text);
        StringBuilder rebuilt = new StringBuilder();
        for (var passage : passages.values()) {
            assertEquals(1, passage.sourceId());
            rebuilt.append(passage.text().substring(rebuilt.isEmpty() ? 0 : 300));
        }
        assertEquals(text, rebuilt.toString());
    }
    @Test void invalidEvidenceHasSpecificSafeDiagnosticAndHonestFallback() throws Exception {
        var http = new FakeHttp(); http.searchReplies.add(discovery());
        http.groqReplies.add(completion(mapper.valueToTree(Map.of("content", assessment("full", "Open [99]", "").toString()))));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UrecChatService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var answer = new UrecChatService(new UrecLivePages(http), gateway(http), search(http)).answer(question("Is urec open rn?"));
            assertEquals("excerpts", answer.mode());
            assertTrue(answer.reply().contains("couldn't verify the generated answer"));
            assertFalse(answer.reply().contains("temporarily unavailable"));
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("reason=UNSUPPORTED_CITATION")));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void openNowUsesLiveHoursAndAttachesCitationWithoutSearch() throws Exception {
        var http = new FakeHttp();
        http.html = "<title>Hours</title><article><h2>Special schedule</h2><p>Today the UREC Center opens at 10am and closes at 4pm. This replaces regular hours.</p></article>";
        http.groqReplies.add(completion(mapper.valueToTree(Map.of("content",
                assessment("full", "No—UREC Center is closed according to its posted schedule. Today's hours are 10 AM–4 PM Eastern.", "").toString()))));
        var answer = new UrecChatService(new UrecLivePages(http), gateway(http), search(http)).answer(question("Is urec open rn?"));
        assertEquals("generated", answer.mode());
        assertTrue(answer.reply().startsWith("No")); assertTrue(answer.reply().endsWith("[1]"));
        assertEquals(URL, answer.sources().get(0).url());
        assertTrue(http.calls.stream().noneMatch(u -> u.getHost().equals("api.tavily.com")));
        assertEquals(1, http.calls.stream().filter(u -> u.getPath().equals("/hours")).count());
        assertTrue(http.lastBody.toString().contains("10am and closes at 4pm"));
        assertTrue(http.lastBody.toString().contains("dated exceptions over regular hours"));
    }
    @Test void missingInlineCitationsNeverAllowsMissingEvidence() throws Exception {
        var source = List.of(new UrecChatService.Source(1, "Hours", URL, Instant.EPOCH.toString()));
        var result = assessment("full", "Open now", "");
        ((com.fasterxml.jackson.databind.node.ObjectNode) result).putArray("evidence");
        assertThrows(EvidenceValidationException.class,
                () -> UrecChatService.validate(result, source, UrecChatService.evidencePassages(1, TEXT)));
        assertFalse(UrecChatService.isHoursQuestion("What classes are open for registration?"));
        assertFalse(UrecChatService.isHoursQuestion("When is membership registration open?"));
        assertTrue(UrecChatService.isHoursQuestion("Is urec open rn?"));
    }

}
