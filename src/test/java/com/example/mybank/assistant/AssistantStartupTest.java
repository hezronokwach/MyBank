package com.example.mybank.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application with no GROQ_API_KEY and no local dev config, so it runs anywhere.
 * No model is called.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistant-startup;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
class AssistantStartupTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AssistantPrompt prompt;

    @Test
    void startsWithoutGroqKeyAndOnlyTheChatModel() {
        assertThat(context.getBeanProvider(ChatModel.class).getIfAvailable()).isNotNull();
        assertThat(context.getBeanProvider(ChatClient.Builder.class).getIfAvailable()).isNotNull();
        assertThat(context.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
    }

    @Test
    void loadsVersionedSystemPrompt() {
        assertThat(prompt.version()).isEqualTo("v1");
        assertThat(prompt.text())
                .contains("Use only the tools")
                .contains("Never ask for, accept or repeat a PIN")
                .contains("last 4 digits")
                .contains("English or Swahili");
    }
}
