package com.ureclive.urec_live_backend.rag;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class GroqGateway {
    private final LiveHttp http;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String key, model;
    public GroqGateway(LiveHttp http, ObjectMapper mapper,
            @Value("${urec.rag.groq-url:https://api.groq.com/openai/v1}") String origin,
            @Value("${GROQ_API_KEY:}") String key,
            @Value("${urec.rag.model:openai/gpt-oss-120b}") String model) {
        this.http = http; this.mapper = mapper; this.key = key.trim(); this.model = model;
        endpoint = URI.create(origin.replaceAll("/+$", "") + "/chat/completions");
    }
    private JsonNode call(Map<String, Object> body, long deadline) throws Exception {
        if (key.isBlank()) throw new RetrievalFailure(RetrievalFailure.Code.MISSING_KEY);
        var response = http.request(endpoint, mapper.writeValueAsString(body),
                Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json"), deadline, 1_000_000);
        if (response.status() != 200) throw new RetrievalFailure(RetrievalFailure.Code.PROVIDER_HTTP, response.status());
        JsonNode root;
        try { root = mapper.readTree(response.body()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new RetrievalFailure(RetrievalFailure.Code.INVALID_RESPONSE); }
        if (root == null) throw new RetrievalFailure(RetrievalFailure.Code.INVALID_RESPONSE);
        var choice = root.path("choices").path(0);
        if (choice.path("finish_reason").asText().equals("length"))
            throw new RetrievalFailure(RetrievalFailure.Code.TRUNCATED_RESPONSE);
        var message = choice.path("message");
        if (!message.path("content").isTextual() || message.path("content").asText().isBlank())
            throw new RetrievalFailure(RetrievalFailure.Code.EMPTY_RESPONSE);
        return message;
    }
    public JsonNode assess(List<UrecChatService.Message> messages, long deadline) throws Exception {
        var response = call(Map.of("model", model, "stream", false, "messages", messages,
                "temperature", 0.1, "max_completion_tokens", 2000,
                "response_format", Map.of("type", "json_object")), deadline);
        try {
            var result = mapper.readTree(response.path("content").asText());
            if (result == null || !result.isObject()) throw new RetrievalFailure(RetrievalFailure.Code.INVALID_RESPONSE);
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RetrievalFailure(RetrievalFailure.Code.INVALID_RESPONSE);
        }
    }
}
