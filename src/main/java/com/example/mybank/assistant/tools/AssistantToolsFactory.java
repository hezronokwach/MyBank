package com.example.mybank.assistant.tools;

import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.AccountService;
import com.example.mybank.services.TransactionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

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

    public AssistantToolsFactory(UserRepository userRepository, AccountService accountService,
                                 TransactionQueryService transactionQueryService, AccountResolver accountResolver,
                                 AssistantToolCallRepository toolCallRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.transactionQueryService = transactionQueryService;
        this.accountResolver = accountResolver;
        this.toolCallRepository = toolCallRepository;
        this.objectMapper = objectMapper;
    }

    public AssistantTools forUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));
        return new AssistantTools(email, user.getId(), accountService, transactionQueryService,
                accountResolver, toolCallRepository, objectMapper);
    }
}
