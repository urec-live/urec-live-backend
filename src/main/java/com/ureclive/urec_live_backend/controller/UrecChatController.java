package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.rag.UrecChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class UrecChatController {
    public record Message(@NotNull @Pattern(regexp = "user|assistant") String role,
                          @NotBlank @Size(max = 8000) String content) {}
    public record Request(@NotEmpty @Size(max = 20) List<@NotNull @Valid Message> messages) {}
    private final UrecChatService service;

    public UrecChatController(UrecChatService service) { this.service = service; }

    @PostMapping
    public UrecChatService.Answer chat(@Valid @RequestBody Request request) {
        if (!request.messages().get(request.messages().size() - 1).role().equals("user")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last message must be from the user");
        }
        return service.answer(request.messages().stream()
                .map(m -> new UrecChatService.Message(m.role(), m.content())).toList());
    }
}
