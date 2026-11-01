package com.ureclive.urec_live_backend.config;

import com.ureclive.urec_live_backend.security.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Autowired private JwtUtil jwtUtil;

    @Autowired private com.ureclive.urec_live_backend.repository.UserRepository users;

    @org.springframework.beans.factory.annotation.Value("${app.gym-scope.enabled:true}")
    private boolean gymScopeEnabled;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Must use getAccessor() — wrap() creates a read-only copy and setUser() is silently lost
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtUtil.validateToken(token)) {
                        String username = jwtUtil.extractUsername(token);
                        accessor.setUser(() -> username);
                    }
                } catch (Exception ignored) {
                    // invalid token — connection continues without a Principal
                }
            }
        }
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (gymScopeEnabled
                    && destination != null
                    && (destination.equals("/topic/machines")
                            || destination.startsWith("/topic/gyms/"))) {
                var principal = accessor.getUser();
                if (principal == null)
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Sign in first");
                var user =
                        users.findByUsername(principal.getName())
                                .orElseThrow(
                                        () ->
                                                new org.springframework.security.access
                                                        .AccessDeniedException("Unknown user"));
                boolean admin =
                        user.getRoles().stream()
                                .anyMatch(
                                        role ->
                                                (role.getName().equals("ROLE_ADMIN")
                                                        || role.getName().equals("ADMIN")));
                if (!admin
                        && (user.getConnectedGymId() == null
                                || !destination.equals(
                                        "/topic/gyms/" + user.getConnectedGymId() + "/machines")))
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Gym connection required");
            }
        }
        return message;
    }
}
