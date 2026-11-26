package com.ureclive.urec_live_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class WorkoutNudgePush {
    private static final Logger log = LoggerFactory.getLogger(WorkoutNudgePush.class);
    private static final URI SEND = URI.create("https://exp.host/--/api/v2/push/send");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final JdbcTemplate db;
    private final String accessToken;

    public WorkoutNudgePush(ObjectMapper json, JdbcTemplate db,
                            @Value("${EXPO_PUSH_ACCESS_TOKEN:}") String accessToken) {
        this.json = json;
        this.db = db;
        this.accessToken = accessToken;
    }

    public void send(List<String> tokens, String sender, String recipient) {
        for (String token : tokens) {
            try {
                var request = HttpRequest.newBuilder(SEND).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                                "to", token, "title", "Workout nudge",
                                "body", "A friend sent you a workout nudge.",
                                "channelId", "workout-nudges", "sound", "default",
                                "data", Map.of("kind", "workout-nudge", "sender", sender, "recipient", recipient)))));
                if (!accessToken.isBlank()) request.header("Authorization", "Bearer " + accessToken);
                client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                        .whenComplete((response, error) -> {
                            if (error != null || response.statusCode() != 200) {
                                log.warn("Could not submit a workout nudge to Expo");
                                return;
                            }
                            try {
                                var ticket = json.readTree(response.body()).path("data");
                                if ("DeviceNotRegistered".equals(ticket.path("details").path("error").asText())) {
                                    db.update("DELETE FROM workout_nudge_devices WHERE token = ?", token);
                                } else if (!"ok".equals(ticket.path("status").asText())) {
                                    log.warn("Expo rejected a workout nudge");
                                }
                            } catch (Exception invalidResponse) {
                                log.warn("Could not read the Expo workout nudge ticket");
                            }
                        });
            } catch (Exception preparationError) {
                log.warn("Could not prepare a workout nudge notification");
            }
        }
    }
}
