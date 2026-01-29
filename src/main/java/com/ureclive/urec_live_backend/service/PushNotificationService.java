package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.PushToken;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.PushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings("null")
public class PushNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushTokenRepository pushTokenRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${expo.push.url:https://exp.host/--/api/v2/push/send}")
    private String pushUrl;

    @Value("${expo.push.enabled:true}")
    private boolean pushEnabled;

    public PushNotificationService(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    public void sendToUser(User user, String title, String body, Map<String, Object> data) {
        if (!pushEnabled) {
            logger.info("[push] disabled, skipping");
            return;
        }
        if (user == null) {
            return;
        }

        if (Boolean.FALSE.equals(user.getPushNotificationsEnabled())) {
            logger.info("[push] user {} has disabled notifications, skipping", user.getId());
            return;
        }

        List<PushToken> tokens = pushTokenRepository.findByUser(user);
        if (tokens.isEmpty()) {
            logger.info("[push] no tokens for user {}", user.getId());
            return;
        }

        String safeTitle = title == null ? "Session update" : title;
        String safeBody = body == null ? "" : body;
        List<Map<String, Object>> messages = new ArrayList<>();
        for (PushToken token : tokens) {
            String tokenValue = token.getToken();
            if (tokenValue == null || !isValidExpoToken(tokenValue)) {
                logger.warn("[push] invalid token for user {} token={}", user.getId(), tokenValue);
                continue;
            }
            Map<String, Object> msg = new HashMap<>();
            msg.put("to", tokenValue);
            msg.put("title", safeTitle);
            msg.put("body", safeBody);
            msg.put("sound", "default");
            if (data != null) {
                msg.put("data", data);
            }
            messages.add(msg);
        }

        if (messages.isEmpty()) {
            return;
        }

        String safePushUrl = (pushUrl == null || pushUrl.isBlank())
                ? "https://exp.host/--/api/v2/push/send"
                : pushUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(messages, headers);
        try {
            restTemplate.postForEntity(java.util.Objects.requireNonNull(safePushUrl), request, String.class);
            logger.info("[push] sent {} messages", messages.size());
        } catch (Exception ex) {
            logger.error("[push] failed to send notifications", ex);
        }
    }

    private boolean isValidExpoToken(String token) {
        if (token == null) {
            return false;
        }
        return token.startsWith("ExponentPushToken") || token.startsWith("ExpoPushToken");
    }
}
