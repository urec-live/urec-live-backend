package com.ureclive.urec_live_backend.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class TavilyGateway {
    public record Discovery(List<String> urls, boolean searched) {}
    private final LiveHttp http;
    private final ObjectMapper mapper;
    private final String key;
    private final URI endpoint;

    public TavilyGateway(LiveHttp http, ObjectMapper mapper,
            @Value("${TAVILY_API_KEY:}") String key,
            @Value("${urec.rag.tavily-url:https://api.tavily.com/search}") String endpoint) {
        this.http = http; this.mapper = mapper; this.key = key.trim(); this.endpoint = URI.create(endpoint);
    }

    public Discovery discover(String query, long deadline) throws Exception {
        if (key.isBlank()) throw new RetrievalFailure(RetrievalFailure.Code.MISSING_KEY);
        // Bound provider input independently of the conversation's larger message limit.
        String searchQuery = query.substring(0, Math.min(query.length(), 400));
        var body = Map.of("query", searchQuery, "include_domains", List.of("urec.charlotte.edu"),
                "search_depth", "basic", "topic", "general", "max_results", 5,
                "include_answer", false, "include_raw_content", false, "auto_parameters", false);
        var response = http.request(endpoint, mapper.writeValueAsString(body),
                Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json"),
                LiveHttp.within(deadline, 15), 1_000_000);
        if (response.status() != 200) throw new RetrievalFailure(RetrievalFailure.Code.PROVIDER_HTTP, response.status());
        com.fasterxml.jackson.databind.JsonNode root;
        try { root = mapper.readTree(response.body()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new RetrievalFailure(RetrievalFailure.Code.INVALID_RESPONSE); }
        if (root == null || !root.path("results").isArray()) throw new RetrievalFailure(RetrievalFailure.Code.INVALID_RESPONSE);
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (var result : root.path("results")) {
            try { urls.add(UrecLivePages.allowed(result.path("url").asText()).toString()); }
            catch (IllegalArgumentException ignored) { }
        }
        // Search snippets/answers never become evidence. Pages are fetched separately.
        return new Discovery(urls.stream().limit(3).toList(), true);
    }
}
