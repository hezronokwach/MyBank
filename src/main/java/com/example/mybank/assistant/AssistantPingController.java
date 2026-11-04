package com.example.mybank.assistant;

import com.example.mybank.assistant.tools.AssistantToolsFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY: checks that the model is reachable with the system prompt and the read tools,
 * using the logged-in admin's own accounts.
 * Admin-only so the free Groq quota can't be used by every customer. Replaced by the chat endpoint (#12).
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantPingController {

    private static final Logger log = LoggerFactory.getLogger(AssistantPingController.class);

    private final ChatClient chatClient;
    private final AssistantPrompt prompt;
    private final AssistantToolsFactory toolsFactory;

    public AssistantPingController(ChatClient.Builder chatClientBuilder, AssistantPrompt prompt,
                                   AssistantToolsFactory toolsFactory) {
        this.chatClient = chatClientBuilder.build();
        this.prompt = prompt;
        this.toolsFactory = toolsFactory;
    }

    public record PingRequest(@NotBlank @Size(max = 500) String message) {}

    public record PingResponse(String reply, String promptVersion, long latencyMs) {}

    @PostMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PingResponse> ping(@Valid @RequestBody PingRequest request, Authentication authentication) {
        long start = System.currentTimeMillis();
        try {
            String reply = chatClient.prompt()
                    .system(prompt.text())
                    .user(request.message())
                    .tools(toolsFactory.forUser(authentication.getName()))
                    .call()
                    .content();
            return ResponseEntity.ok(new PingResponse(reply, prompt.version(), System.currentTimeMillis() - start));
        } catch (RuntimeException e) {
            log.warn("Assistant model call failed after {} ms: {}", System.currentTimeMillis() - start, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new PingResponse("The assistant isn't available right now.", prompt.version(),
                            System.currentTimeMillis() - start));
        }
    }
}
