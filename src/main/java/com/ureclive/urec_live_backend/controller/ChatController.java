package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.ChatRequest;
import com.ureclive.urec_live_backend.dto.ChatResponse;
import com.ureclive.urec_live_backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String reply = chatService.chat(request.getMessages(), userDetails.getUsername());
        return ResponseEntity.ok(new ChatResponse(reply));
    }
}
