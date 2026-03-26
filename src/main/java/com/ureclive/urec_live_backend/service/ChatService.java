package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.ChatRequest;
import com.ureclive.urec_live_backend.dto.PersonalRecordResponse;
import com.ureclive.urec_live_backend.dto.SessionStatsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private final WorkoutSessionService sessionService;
    private final RestTemplate restTemplate;

    public ChatService(WorkoutSessionService sessionService) {
        this.sessionService = sessionService;
        this.restTemplate = new RestTemplate();
    }

    public String chat(List<ChatRequest.ChatMessage> messages, String username) {
        if (apiKey == null || apiKey.isBlank()) {
            return "AI assistant is not configured. Please set the Anthropic API key in the backend configuration.";
        }

        String systemPrompt = buildSystemPrompt(username);

        // Build request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);

        List<Map<String, String>> apiMessages = messages.stream()
                .map(m -> {
                    Map<String, String> msg = new LinkedHashMap<>();
                    msg.put("role", m.getRole());
                    msg.put("content", m.getContent());
                    return msg;
                })
                .collect(Collectors.toList());
        body.put("messages", apiMessages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ANTHROPIC_API_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("content")) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");
                if (!content.isEmpty()) {
                    return (String) content.get(0).get("text");
                }
            }
            return "I couldn't generate a response. Please try again.";
        } catch (Exception e) {
            return "Sorry, I'm having trouble connecting right now. Please try again later.";
        }
    }

    private String buildSystemPrompt(String username) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are UREC AI, the fitness assistant for the UREC Live gym app. ");
        sb.append("You help gym members with workout advice, exercise form tips, ");
        sb.append("training program suggestions, and interpreting their workout data. ");
        sb.append("Be encouraging, concise, and practical. Use the member's workout data below to personalize your advice.\n\n");
        sb.append("The user's name is: ").append(username).append("\n\n");

        try {
            SessionStatsResponse stats = sessionService.getUserStats(username);
            sb.append("--- USER WORKOUT DATA ---\n");
            sb.append("Total workouts: ").append(stats.getTotalSessions()).append("\n");

            long totalSecs = stats.getTotalDurationSeconds();
            long hours = totalSecs / 3600;
            long mins = (totalSecs % 3600) / 60;
            sb.append("Total time: ").append(hours).append("h ").append(mins).append("m\n");

            sb.append("Total volume lifted: ").append(String.format("%.0f", stats.getTotalVolumeLbs())).append(" lbs\n");
            sb.append("Current streak: ").append(stats.getCurrentStreak()).append(" days\n");
            sb.append("Longest streak: ").append(stats.getLongestStreak()).append(" days\n");

            if (stats.getTopExercises() != null && !stats.getTopExercises().isEmpty()) {
                sb.append("\nTop exercises:\n");
                for (SessionStatsResponse.ExerciseCount ex : stats.getTopExercises()) {
                    sb.append("  - ").append(ex.getName()).append(": ").append(ex.getCount()).append(" sessions\n");
                }
            }

            if (stats.getMuscleGroupBreakdown() != null && !stats.getMuscleGroupBreakdown().isEmpty()) {
                sb.append("\nMuscle group breakdown:\n");
                for (Map.Entry<String, Long> entry : stats.getMuscleGroupBreakdown().entrySet()) {
                    sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" sessions\n");
                }
            }

            if (stats.getSessionsPerWeek() != null && !stats.getSessionsPerWeek().isEmpty()) {
                sb.append("\nRecent weekly activity:\n");
                for (Map.Entry<String, Long> entry : stats.getSessionsPerWeek().entrySet()) {
                    sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" sessions\n");
                }
            }
        } catch (Exception e) {
            sb.append("(No workout history available for this user yet.)\n");
        }

        try {
            List<PersonalRecordResponse> prs = sessionService.getPersonalRecords(username);
            if (prs != null && !prs.isEmpty()) {
                sb.append("\nPersonal records:\n");
                for (PersonalRecordResponse pr : prs) {
                    sb.append("  - ").append(pr.getExerciseName()).append(": ")
                      .append(String.format("%.0f", pr.getMaxWeightLbs())).append(" lbs\n");
                }
            }
        } catch (Exception e) {
            // no PRs available
        }

        sb.append("--- END USER DATA ---\n\n");
        sb.append("Keep responses concise (2-3 short paragraphs max). ");
        sb.append("Use bullet points when listing exercises or tips. ");
        sb.append("If the user has no data yet, encourage them to start logging workouts.");

        return sb.toString();
    }
}
