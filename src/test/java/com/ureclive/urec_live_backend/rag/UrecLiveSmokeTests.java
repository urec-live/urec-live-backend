package com.ureclive.urec_live_backend.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfSystemProperty(named = "urec.live-smoke", matches = "true")
class UrecLiveSmokeTests {
    @Test void openNowWithLiveHoursAndGroq() {
        String key = System.getenv("GROQ_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "GROQ_API_KEY required; never print credentials");
        var http = new LiveHttp();
        var groq = new GroqGateway(http, new ObjectMapper(), "https://api.groq.com/openai/v1", key,
                System.getenv().getOrDefault("UREC_RAG_MODEL", "openai/gpt-oss-120b"));
        var search = new TavilyGateway(http, new ObjectMapper(), "", "https://api.tavily.com/search");
        var answer = new UrecChatService(new UrecLivePages(http), groq, search).answer(
                List.of(new UrecChatService.Message("user", "Is urec open rn?")));
        assertEquals("generated", answer.mode());
        assertEquals(1, answer.sources().size());
        assertTrue(answer.reply().contains("[1]"));
    }
    @Test void officialPageAndCache() throws Exception {
        var pages = new UrecLivePages(new LiveHttp());
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        var first = pages.fetch("https://urec.charlotte.edu/hours", deadline);
        assertEquals("Hours", first.page().title()); assertFalse(first.cached());
        var second = pages.fetch("https://urec.charlotte.edu/hours", deadline);
        assertTrue(second.cached()); assertEquals(first.page().fetchedAt(), second.page().fetchedAt());
    }
    @Test void tavilySearchAndGroundedAnswer() throws Exception {
        String key = System.getenv("GROQ_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "GROQ_API_KEY required; never print credentials");
        String searchKey = System.getenv("TAVILY_API_KEY");
        assumeTrue(searchKey != null && !searchKey.isBlank(), "TAVILY_API_KEY required; never print credentials");
        var http = new LiveHttp();
        var search = new TavilyGateway(http, new ObjectMapper(), searchKey, "https://api.tavily.com/search");
        var groq = new GroqGateway(http, new ObjectMapper(), "https://api.groq.com/openai/v1", key,
                System.getenv().getOrDefault("UREC_RAG_MODEL", "openai/gpt-oss-120b"));
        var discovery = search.discover("UREC membership eligibility", System.nanoTime() + Duration.ofSeconds(20).toNanos());
        assertTrue(discovery.searched()); assertFalse(discovery.urls().isEmpty());
        var answer = new UrecChatService(new UrecLivePages(http), groq, search).answer(
                List.of(new UrecChatService.Message("user", "Who is eligible for UREC membership?")));
        assertEquals("generated", answer.mode()); assertFalse(answer.sources().isEmpty());
    }
}
