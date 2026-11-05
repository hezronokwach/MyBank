package com.example.mybank.assistant.tools;

import com.example.mybank.assistant.PendingActionService;
import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.AccountService;
import com.example.mybank.services.TransactionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Creates the tools for one answer, bound to the customer from the JWT (never from the chat).
 */
@Component
public class AssistantToolsFactory {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final TransactionQueryService transactionQueryService;
    private final AccountResolver accountResolver;
    private final AssistantToolCallRepository toolCallRepository;
    private final ObjectMapper objectMapper;
    private final PendingActionService pendingActionService;

    public AssistantToolsFactory(UserRepository userRepository, AccountService accountService,
                                 TransactionQueryService transactionQueryService, AccountResolver accountResolver,
                                 AssistantToolCallRepository toolCallRepository, ObjectMapper objectMapper,
                                 PendingActionService pendingActionService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.transactionQueryService = transactionQueryService;
        this.accountResolver = accountResolver;
        this.toolCallRepository = toolCallRepository;
        this.objectMapper = objectMapper;
        this.pendingActionService = pendingActionService;
    }

    // Tools for one answer in a conversation; pending actions are linked to that conversation
    public AssistantTools forConversation(String email, UUID conversationId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));
        return new AssistantTools(email, user.getId(), conversationId, accountService, transactionQueryService,
                accountResolver, toolCallRepository, objectMapper, pendingActionService);
    }

    // Tools with no conversation, used by tests
    public AssistantTools forUser(String email) {
        return forConversation(email, null);
    }
}
