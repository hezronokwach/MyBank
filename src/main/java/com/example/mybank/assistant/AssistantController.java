package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.ChatAnswer;
import com.example.mybank.assistant.dto.ChatRequest;
import com.example.mybank.assistant.entity.MessageOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * POST /api/v1/assistant/chat
     * Ask the assistant a question. The customer always comes from the JWT, never from the request body.
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ChatAnswer> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        ChatAnswer answer = assistantService.chat(userEmail, request.conversationId(), request.message());

        if (answer.outcome() == MessageOutcome.RATE_LIMITED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(answer);
        }
        return ResponseEntity.ok(answer);
    }
}
