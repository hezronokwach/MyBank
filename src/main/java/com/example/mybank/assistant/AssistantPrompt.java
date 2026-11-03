package com.example.mybank.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The versioned system prompt. The version is saved with every answer so evaluation results
 * can be tied to the prompt that produced them.
 */
@Component
public class AssistantPrompt {

    private final String version;
    private final String text;

    public AssistantPrompt(@Value("${app.assistant.prompt-version:v1}") String version) {
        this.version = version;
        ClassPathResource resource = new ClassPathResource("prompts/assistant-" + version + ".txt");
        try {
            this.text = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("System prompt " + resource.getPath() + " not found", e);
        }
    }

    public String version() {
        return version;
    }

    public String text() {
        return text;
    }
}
