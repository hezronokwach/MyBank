package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.ChatAnswer;
import com.example.mybank.assistant.dto.PendingActionView;
import com.example.mybank.assistant.entity.AssistantConversation;
import com.example.mybank.assistant.entity.AssistantToolCall;
import com.example.mybank.assistant.entity.ConversationMessage;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.assistant.entity.MessageRole;
import com.example.mybank.assistant.entity.ToolOutcome;
import com.example.mybank.assistant.repository.AssistantConversationRepository;
import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.assistant.repository.ConversationMessageRepository;
import com.example.mybank.assistant.tools.AssistantTools;
import com.example.mybank.assistant.tools.AssistantToolsFactory;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.ConversationNotFoundException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs one turn of the chat:
 *   1. check the rate limit
 *   2. load the customer's conversation (or start a new one) and its last 10 messages
 *   3. save the customer's message
 *   4. ask the model, giving it the system prompt, the history and the tools
 *   5. clean the answer with OutputGuard, then save it with its prompt version, model, tokens, latency and outcome
 *   6. link this turn's tool-call audit rows to the answer
 *
 * There is deliberately no @Transactional here: the model call can take up to 10 seconds,
 * and a database transaction should not stay open that long. Each save is its own small transaction.
 */
@Service
public class AssistantService {

    static final String FALLBACK_TEXT =
            "The assistant isn't available right now. You can still check balances and transactions from your Dashboard.";
    static final String RATE_LIMITED_TEXT = "Please wait a moment before sending another message.";
    static final int TITLE_LENGTH = 120;

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatClient chatClient;
    private final AssistantPrompt prompt;
    private final AssistantToolsFactory toolsFactory;
    private final RateLimiter rateLimiter;
    private final UserRepository userRepository;
    private final AssistantConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AssistantToolCallRepository toolCallRepository;
    private final OutputGuard outputGuard;
    private final AssistantMetrics metrics;
    private final String configuredModel;

    public AssistantService(ChatClient.Builder chatClientBuilder,
                            AssistantPrompt prompt,
                            AssistantToolsFactory toolsFactory,
                            RateLimiter rateLimiter,
                            UserRepository userRepository,
                            AssistantConversationRepository conversationRepository,
                            ConversationMessageRepository messageRepository,
                            AssistantToolCallRepository toolCallRepository,
                            OutputGuard outputGuard,
                            AssistantMetrics metrics,
                            @Value("${spring.ai.openai.chat.options.model:unknown}") String configuredModel) {
        this.chatClient = chatClientBuilder.build();
        this.prompt = prompt;
        this.toolsFactory = toolsFactory;
        this.rateLimiter = rateLimiter;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.toolCallRepository = toolCallRepository;
        this.outputGuard = outputGuard;
        this.metrics = metrics;
        this.configuredModel = configuredModel;
    }

    public ChatAnswer chat(String email, UUID conversationId, String message) {
        // 1. Rate limit: nothing is saved and the model isn't called
        if (!rateLimiter.tryAcquire(email)) {
            metrics.recordRateLimited();
            return new ChatAnswer(conversationId, null, RATE_LIMITED_TEXT, MessageOutcome.RATE_LIMITED,
                    new ArrayList<>(), new ArrayList<>());
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        // 2. Load the conversation, only if it belongs to this user, or start a new one
        AssistantConversation conversation;
        if (conversationId != null) {
            conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                    .orElseThrow(() -> new ConversationNotFoundException("Conversation " + conversationId + " not found"));
        } else {
            conversation = conversationRepository.save(new AssistantConversation(user.getId(), makeTitle(outputGuard.clean(message))));
        }

        // History is loaded before saving the new message, so it holds only the earlier ones
        List<Message> history = loadHistory(conversation.getId());

        // 3. Save the customer's message. It is cleaned first, so a PIN the customer typed is never stored.
        //    (The model still gets the original, so it can warn them not to share their PIN.)
        messageRepository.save(new ConversationMessage(conversation.getId(), MessageRole.USER, outputGuard.clean(message)));

        // 4. Ask the model
        AssistantTools tools = toolsFactory.forConversation(email, conversation.getId());
        long start = System.currentTimeMillis();

        String text;
        MessageOutcome outcome;
        String model = configuredModel;
        Integer inputTokens = null;
        Integer outputTokens = null;

        try {
            ChatResponse response = chatClient.prompt()
                    .system(prompt.text())
                    .messages(history)
                    .user(message)
                    .tools(tools)
                    .call()
                    .chatResponse();

            text = readText(response);
            if (text == null || text.isBlank()) {
                // The model sometimes returns nothing at all; treat that as a failure
                text = FALLBACK_TEXT;
                outcome = MessageOutcome.FALLBACK;
            } else {
                // 5. Catch anything the model should never show: account numbers, PINs, tokens
                String cleaned = outputGuard.clean(text);
                if (!cleaned.equals(text)) {
                    metrics.recordBlocked(AssistantMetrics.BLOCKED_OUTPUT_GUARD);
                }
                text = cleaned;
                outcome = MessageOutcome.OK;
            }

            if (response != null && response.getMetadata() != null) {
                if (response.getMetadata().getModel() != null && !response.getMetadata().getModel().isBlank()) {
                    model = response.getMetadata().getModel();
                }
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    inputTokens = usage.getPromptTokens();
                    outputTokens = usage.getCompletionTokens();
                }
            }
        } catch (RuntimeException e) {
            // Timeout, provider error or a tool that kept failing: the customer gets the fallback message
            text = FALLBACK_TEXT;
            if (isProviderRateLimit(e)) {
                outcome = MessageOutcome.RATE_LIMITED_PROVIDER;
            } else {
                outcome = MessageOutcome.FALLBACK;
            }
            log.warn("Assistant answer failed ({}) after {} ms: {}", outcome, System.currentTimeMillis() - start, e.getMessage());
        }

        long latencyMs = System.currentTimeMillis() - start;

        // Save the answer
        ConversationMessage answer = messageRepository.save(new ConversationMessage(
                conversation.getId(), text, prompt.version(), model, inputTokens, outputTokens, latencyMs, outcome));

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        // 6. Link this turn's tool calls to the answer, and list what the answer was based on
        List<AssistantToolCall> toolCalls = linkToolCalls(tools.recordedCallIds(), answer.getId());
        List<String> sources = describeSources(toolCalls);

        metrics.recordAnswer(outcome, latencyMs, inputTokens, outputTokens);

        // One line per answer, for searching the logs. Never the message text: it may hold personal details.
        List<String> toolNames = new ArrayList<>();
        for (AssistantToolCall toolCall : toolCalls) {
            toolNames.add(toolCall.getToolName());
        }
        log.info("Assistant answer conversation={} user={} outcome={} latencyMs={} inputTokens={} outputTokens={} prompt={} tools={}",
                conversation.getId(), user.getId(), outcome, latencyMs, inputTokens, outputTokens, prompt.version(), toolNames);

        // Confirm cards for anything the assistant prepared in this turn
        List<PendingActionView> pendingActions = tools.createdPendingActions();

        return new ChatAnswer(conversation.getId(), answer.getId(), text, outcome, sources, pendingActions);
    }

    // The last 10 messages, oldest first, as Spring AI messages.
    // Failed answers (fallback, rate limited) are left out so the model doesn't repeat them.
    private List<Message> loadHistory(UUID conversationId) {
        List<ConversationMessage> newestFirst = messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);

        List<Message> history = new ArrayList<>();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            ConversationMessage saved = newestFirst.get(i);
            if (saved.getRole() == MessageRole.USER) {
                history.add(new UserMessage(saved.getContent()));
            } else if (saved.getOutcome() == MessageOutcome.OK) {
                history.add(new AssistantMessage(saved.getContent()));
            }
        }
        return history;
    }

    List<AssistantToolCall> linkToolCalls(List<UUID> toolCallIds, UUID messageId) {
        List<AssistantToolCall> toolCalls = toolCallRepository.findAllById(toolCallIds);
        for (AssistantToolCall toolCall : toolCalls) {
            toolCall.setMessageId(messageId);
        }
        return toolCallRepository.saveAll(toolCalls);
    }

    // Turns the tools that worked into words for the "Based on" line under the answer
    private List<String> describeSources(List<AssistantToolCall> toolCalls) {
        List<String> sources = new ArrayList<>();
        for (AssistantToolCall toolCall : toolCalls) {
            if (toolCall.getOutcome() != ToolOutcome.OK) {
                continue;
            }

            String source;
            if (toolCall.getToolName().equals("listMyAccounts")) {
                source = "your accounts";
            } else if (toolCall.getToolName().equals("getAccount")) {
                source = "account details";
            } else if (toolCall.getToolName().equals("getRecentTransactions")) {
                source = "recent transactions";
            } else if (toolCall.getToolName().equals("findTransaction")) {
                source = "a transaction lookup";
            } else if (toolCall.getToolName().equals("requestFreezeAccount")) {
                source = "a freeze request";
            } else {
                source = toolCall.getToolName();
            }

            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private String readText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private String makeTitle(String message) {
        String title = message.trim();
        if (title.length() > TITLE_LENGTH) {
            title = title.substring(0, TITLE_LENGTH);
        }
        return title;
    }

    // Groq answers "HTTP 429" when its own usage limit is reached; the message may be on a wrapped cause
    private boolean isProviderRateLimit(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("429")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
