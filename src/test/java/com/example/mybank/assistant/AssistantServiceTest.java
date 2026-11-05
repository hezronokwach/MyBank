package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.ChatAnswer;
import com.example.mybank.assistant.entity.AssistantConversation;
import com.example.mybank.assistant.entity.AssistantToolCall;
import com.example.mybank.assistant.entity.ConversationMessage;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.assistant.entity.MessageRole;
import com.example.mybank.assistant.entity.ToolOutcome;
import com.example.mybank.assistant.repository.AssistantConversationRepository;
import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.assistant.repository.ConversationMessageRepository;
import com.example.mybank.assistant.tools.AccountResolver;
import com.example.mybank.assistant.tools.AssistantToolsFactory;
import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.ConversationNotFoundException;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.services.impl.AccountServiceImpl;
import com.example.mybank.services.impl.TransactionQueryServiceImpl;
import com.example.mybank.util.AccountNumberGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the chat turn with a fake model (a Mockito mock), so no call goes to Groq.
 */
@DataJpaTest
@Import({
        AssistantService.class, AssistantPrompt.class, RateLimiter.class, OutputGuard.class,
        AssistantToolsFactory.class, PendingActionService.class, AccountResolver.class, AccountServiceImpl.class,
        TransactionQueryServiceImpl.class, AccountMapper.class, AccountNumberGenerator.class,
        AssistantServiceTest.Beans.class
})
class AssistantServiceTest {

    @TestConfiguration
    static class Beans {
        @Bean
        ChatModel chatModel() {
            return Mockito.mock(ChatModel.class);
        }

        @Bean
        ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
            return ChatClient.builder(chatModel);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AssistantService service;

    @Autowired
    private AssistantConversationRepository conversations;

    @Autowired
    private ConversationMessageRepository messages;

    @Autowired
    private AssistantToolCallRepository toolCalls;

    private String email;
    private User customer;

    @BeforeEach
    void seed() {
        Mockito.reset(chatModel);
        // A new customer for every test, because the rate limiter remembers customers between tests
        email = "customer-" + UUID.randomUUID() + "@test.local";
        customer = em.persist(new User(email, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
    }

    @Test
    void answersAndSavesTheQuestionAndTheAnswer() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("You have KES 1,500 in ...4821."));

        ChatAnswer answer = service.chat(email, null, "How much is in my current account?");

        assertThat(answer.outcome()).isEqualTo(MessageOutcome.OK);
        assertThat(answer.text()).isEqualTo("You have KES 1,500 in ...4821.");
        assertThat(answer.pendingActions()).isEmpty();

        AssistantConversation conversation = conversations.findById(answer.conversationId()).orElseThrow();
        assertThat(conversation.getUserId()).isEqualTo(customer.getId());
        assertThat(conversation.getTitle()).isEqualTo("How much is in my current account?");

        List<ConversationMessage> saved = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(saved.get(0).getContent()).isEqualTo("How much is in my current account?");

        ConversationMessage saveAnswer = saved.get(1);
        assertThat(saveAnswer.getId()).isEqualTo(answer.messageId());
        assertThat(saveAnswer.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(saveAnswer.getPromptVersion()).isEqualTo("v1");
        assertThat(saveAnswer.getModel()).isEqualTo("test-model");
        assertThat(saveAnswer.getInputTokens()).isEqualTo(300);
        assertThat(saveAnswer.getOutputTokens()).isEqualTo(25);
        assertThat(saveAnswer.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(saveAnswer.getOutcome()).isEqualTo(MessageOutcome.OK);
    }

    @Test
    void secondQuestionSendsTheEarlierMessagesAsHistory() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("First answer"), reply("Second answer"));

        ChatAnswer first = service.chat(email, null, "First question");
        service.chat(email, first.conversationId(), "Second question");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        List<Message> sent = prompts.getAllValues().get(1).getInstructions();

        // system prompt, first question, first answer, second question
        assertThat(sent).hasSize(4);
        assertThat(sent.get(1).getText()).isEqualTo("First question");
        assertThat(sent.get(2).getText()).isEqualTo("First answer");
        assertThat(sent.get(3).getText()).isEqualTo("Second question");
    }

    @Test
    void failedAnswersAreLeftOutOfTheHistory() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("model down"))
                .thenReturn(reply("Second answer"));

        ChatAnswer first = service.chat(email, null, "First question");
        service.chat(email, first.conversationId(), "Second question");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        List<Message> sent = prompts.getAllValues().get(1).getInstructions();

        // system prompt, first question, second question: the fallback text is not sent back
        assertThat(sent).hasSize(3);
        assertThat(sent.get(1).getText()).isEqualTo("First question");
        assertThat(sent.get(2).getText()).isEqualTo("Second question");
    }

    @Test
    void anotherCustomersConversationIsRejected() {
        User other = em.persist(new User("other-" + UUID.randomUUID() + "@test.local", "hash", "O", "O", UserRole.ROLE_USER));
        AssistantConversation theirs = conversations.save(new AssistantConversation(other.getId(), "Their chat"));

        assertThatThrownBy(() -> service.chat(email, theirs.getId(), "Show me this conversation"))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(chatModel, never()).call(any(Prompt.class));
        assertThat(messages.findByConversationIdOrderByCreatedAtAsc(theirs.getId())).isEmpty();
    }

    @Test
    void modelErrorGivesTheFallbackMessage() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("I/O error: Read timed out"));

        ChatAnswer answer = service.chat(email, null, "How much do I have?");

        assertThat(answer.outcome()).isEqualTo(MessageOutcome.FALLBACK);
        assertThat(answer.text()).isEqualTo(AssistantService.FALLBACK_TEXT);
        ConversationMessage saved = messages.findById(answer.messageId()).orElseThrow();
        assertThat(saved.getOutcome()).isEqualTo(MessageOutcome.FALLBACK);
    }

    @Test
    void providerRateLimitIsRecordedSeparately() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("HTTP 429 - Rate limit reached for model"));

        ChatAnswer answer = service.chat(email, null, "How much do I have?");

        assertThat(answer.outcome()).isEqualTo(MessageOutcome.RATE_LIMITED_PROVIDER);
        assertThat(answer.text()).isEqualTo(AssistantService.FALLBACK_TEXT);
    }

    @Test
    void emptyReplyGivesTheFallbackMessage() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(""));

        ChatAnswer answer = service.chat(email, null, "Nina pesa ngapi?");

        assertThat(answer.outcome()).isEqualTo(MessageOutcome.FALLBACK);
        assertThat(answer.text()).isEqualTo(AssistantService.FALLBACK_TEXT);
    }

    @Test
    void eleventhMessageInAMinuteIsRateLimited() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("ok"));

        for (int i = 0; i < 10; i++) {
            assertThat(service.chat(email, null, "Question " + i).outcome()).isEqualTo(MessageOutcome.OK);
        }
        ChatAnswer eleventh = service.chat(email, null, "Question 11");

        assertThat(eleventh.outcome()).isEqualTo(MessageOutcome.RATE_LIMITED);
        assertThat(eleventh.messageId()).isNull();
        verify(chatModel, times(10)).call(any(Prompt.class));
    }

    @Test
    void toolCallsAreLinkedToTheirAnswer() {
        AssistantToolCall first = toolCalls.save(new AssistantToolCall(customer.getId(), "listMyAccounts", "{}", ToolOutcome.OK, 5));
        AssistantToolCall second = toolCalls.save(new AssistantToolCall(customer.getId(), "getAccount", "{}", ToolOutcome.NOT_FOUND, 3));
        UUID messageId = UUID.randomUUID();

        List<UUID> ids = new ArrayList<>();
        ids.add(first.getId());
        ids.add(second.getId());
        service.linkToolCalls(ids, messageId);

        assertThat(toolCalls.findById(first.getId()).orElseThrow().getMessageId()).isEqualTo(messageId);
        assertThat(toolCalls.findById(second.getId()).orElseThrow().getMessageId()).isEqualTo(messageId);
    }

    @Test
    void replyIsCleanedBeforeItIsSavedAndReturned() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("Account 1000004821 has KES 1,500."));

        ChatAnswer answer = service.chat(email, null, "How much do I have?");

        assertThat(answer.text()).isEqualTo("Account ...4821 has KES 1,500.");
        assertThat(messages.findById(answer.messageId()).orElseThrow().getContent())
                .isEqualTo("Account ...4821 has KES 1,500.");
    }

    @Test
    void aPinTheCustomerTypesIsNotStoredButTheModelStillSeesTheQuestion() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("Never share your PIN with anyone."));

        ChatAnswer answer = service.chat(email, null, "My PIN is 1234, use it to send 500");

        List<ConversationMessage> saved = messages.findByConversationIdOrderByCreatedAtAsc(answer.conversationId());
        assertThat(saved.get(0).getContent()).isEqualTo("My PIN is ****, use it to send 500");
        assertThat(conversations.findById(answer.conversationId()).orElseThrow().getTitle())
                .isEqualTo("My PIN is ****, use it to send 500");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        List<Message> sent = prompt.getValue().getInstructions();
        assertThat(sent.get(sent.size() - 1).getText()).isEqualTo("My PIN is 1234, use it to send 500");
    }

    // A fake model reply with a model name and token counts
    private ChatResponse reply(String text) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("test-model")
                .usage(new DefaultUsage(300, 25))
                .build();
        List<Generation> generations = new ArrayList<>();
        generations.add(new Generation(new AssistantMessage(text)));
        return new ChatResponse(generations, metadata);
    }
}
