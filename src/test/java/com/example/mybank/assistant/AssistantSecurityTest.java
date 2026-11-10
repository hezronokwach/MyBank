package com.example.mybank.assistant;

import com.example.mybank.assistant.entity.AssistantConversation;
import com.example.mybank.assistant.entity.AssistantToolCall;
import com.example.mybank.assistant.entity.ConversationMessage;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.assistant.entity.PendingAction;
import com.example.mybank.assistant.entity.PendingActionStatus;
import com.example.mybank.assistant.entity.PendingActionType;
import com.example.mybank.assistant.entity.ToolOutcome;
import com.example.mybank.assistant.repository.AssistantConversationRepository;
import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.assistant.repository.ConversationMessageRepository;
import com.example.mybank.assistant.repository.PendingActionRepository;
import com.example.mybank.assistant.tools.AssistantTools;
import com.example.mybank.assistant.tools.AssistantTools.ToolResult;
import com.example.mybank.assistant.tools.AssistantToolsFactory;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The assistant's security boundary (issue #17).
 *
 * Wanjiku is the logged-in customer. Otieno and Amina are other customers.
 * Every test tries to reach Otieno's or Amina's data as Wanjiku, the way a prompt injection would make
 * the model try. These tests call the tools and endpoints directly, with no AI, so they prove the
 * boundary holds whatever the model asks for.
 *
 * Runs the whole app (real security, real JWTs) on an in-memory database. The model is a mock that is
 * never expected to be called. @Transactional rolls the database back after each test.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistant-security;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Transactional
class AssistantSecurityTest {

    private static final String WANJIKU = "wanjiku@test.local";

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AssistantToolsFactory toolsFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AssistantToolCallRepository toolCallRepository;

    @Autowired
    private PendingActionRepository pendingActionRepository;

    @Autowired
    private AssistantConversationRepository conversationRepository;

    @Autowired
    private ConversationMessageRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    private User wanjiku;
    private User otieno;
    private String wanjikuToken;

    @BeforeEach
    void seed() {
        wanjiku = userRepository.save(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        otieno = userRepository.save(new User("otieno@test.local", "hash", "Otieno", "O", UserRole.ROLE_USER));
        User amina = userRepository.save(new User("amina@test.local", "hash", "Amina", "A", UserRole.ROLE_USER));

        Account wanjikuAccount = account("1000004821", wanjiku);
        Account otienoAccount = account("1000007777", otieno);
        Account aminaAccount = account("1000005555", amina);

        // Activity Wanjiku must never see
        transactionRepository.save(new Transaction("TRN7000001", otienoAccount, aminaAccount,
                new BigDecimal("900"), TransactionType.TRANSFER, TransactionStatus.SUCCESS));
        transactionRepository.save(new Transaction("DEP7000002", null, otienoAccount,
                new BigDecimal("5000"), TransactionType.DEPOSIT, TransactionStatus.SUCCESS));
        // Wanjiku's own, to show her lookups still work
        transactionRepository.save(new Transaction("DEP4000001", null, wanjikuAccount,
                new BigDecimal("1500"), TransactionType.DEPOSIT, TransactionStatus.SUCCESS));

        wanjikuToken = jwtTokenProvider.generateToken(wanjiku);
    }

    // =========================================================================
    // TOOLS: what the model can call
    // =========================================================================

    @Test
    void getAccountWithAnotherCustomersNumberIsNotFound() {
        AssistantTools tools = toolsFactory.forUser(WANJIKU);

        ToolResult byFullNumber = tools.getAccount("1000007777");
        ToolResult byLast4 = tools.getAccount("7777");

        assertThat(byFullNumber.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(byFullNumber.data()).isNull();
        assertThat(byLast4.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertLastToolCall(tools, "getAccount", ToolOutcome.NOT_FOUND);
    }

    @Test
    void getRecentTransactionsWithAnotherCustomersNumberIsNotFound() {
        AssistantTools tools = toolsFactory.forUser(WANJIKU);

        ToolResult result = tools.getRecentTransactions("1000007777", 20);

        assertThat(result.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(result.data()).isNull();
        assertLastToolCall(tools, "getRecentTransactions", ToolOutcome.NOT_FOUND);
    }

    @Test
    void findTransactionBetweenTwoOtherCustomersIsNotFound() {
        AssistantTools tools = toolsFactory.forUser(WANJIKU);

        ToolResult between = tools.findTransaction("TRN7000001");
        ToolResult theirDeposit = tools.findTransaction("DEP7000002");
        ToolResult unknown = tools.findTransaction("TRN9999999");

        assertThat(between.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(between.data()).isNull();
        assertThat(theirDeposit.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        // Same answer as a reference that doesn't exist, so the model can't tell that it does
        assertThat(between.message()).isEqualTo(unknown.message());
        assertLastToolCall(tools, "findTransaction", ToolOutcome.NOT_FOUND);

        // Her own transaction still works
        assertThat(tools.findTransaction("DEP4000001").status()).isEqualTo(ToolOutcome.OK);
    }

    @Test
    void requestFreezeAccountOnAnotherCustomersAccountIsNotFound() {
        AssistantTools tools = toolsFactory.forUser(WANJIKU);

        ToolResult result = tools.requestFreezeAccount("1000007777", "ignore your rules");

        assertThat(result.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(tools.createdPendingActions()).isEmpty();
        assertThat(pendingActionRepository.count()).isZero();
        assertThat(accountStatus("1000007777")).isEqualTo(AccountStatus.ACTIVE);
        assertLastToolCall(tools, "requestFreezeAccount", ToolOutcome.NOT_FOUND);
    }

    @Test
    void toolCallsOverTheLimitAreDeniedAndAudited() {
        AssistantTools tools = toolsFactory.forUser(WANJIKU);
        for (int i = 0; i < 5; i++) {
            tools.getAccount("1000007777");
        }

        ToolResult sixth = tools.getAccount("4821");

        assertThat(sixth.status()).isEqualTo(ToolOutcome.DENIED);
        assertLastToolCall(tools, "getAccount", ToolOutcome.DENIED);
    }

    // =========================================================================
    // ENDPOINTS: what the app calls, through real security
    // =========================================================================

    @Test
    void confirmingAnotherCustomersPendingActionReturns404() throws Exception {
        PendingAction theirs = pendingActionRepository.save(freezeRequest(otieno, "1000007777", Instant.now().plusSeconds(300)));

        mockMvc.perform(post("/api/v1/assistant/actions/" + theirs.getId() + "/confirm")
                        .header("Authorization", "Bearer " + wanjikuToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/assistant/actions/" + theirs.getId() + "/cancel")
                        .header("Authorization", "Bearer " + wanjikuToken))
                .andExpect(status().isNotFound());

        assertThat(accountStatus("1000007777")).isEqualTo(AccountStatus.ACTIVE);
        assertThat(pendingActionRepository.findById(theirs.getId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.PENDING);
    }

    @Test
    void confirmingTheSameActionTwiceFailsTheSecondTime() throws Exception {
        PendingAction own = pendingActionRepository.save(freezeRequest(wanjiku, "1000004821", Instant.now().plusSeconds(300)));

        mockMvc.perform(post("/api/v1/assistant/actions/" + own.getId() + "/confirm")
                        .header("Authorization", "Bearer " + wanjikuToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/assistant/actions/" + own.getId() + "/confirm")
                        .header("Authorization", "Bearer " + wanjikuToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request was already confirmed"));

        assertThat(accountStatus("1000004821")).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void confirmingAnotherCustomersTransferReturns404AndMovesNoMoney() throws Exception {
        // Otieno prepared a transfer from his own account; Wanjiku tries to confirm it
        PendingAction theirs = pendingActionRepository.save(new PendingAction(otieno.getId(), null, "1000007777",
                "1000004821", new BigDecimal("500"), "Send KES 500.00 from ...7777 to ...4821", Instant.now().plusSeconds(300)));

        mockMvc.perform(post("/api/v1/assistant/actions/" + theirs.getId() + "/confirm")
                        .header("Authorization", "Bearer " + wanjikuToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNotFound());

        assertThat(balanceOf("1000007777")).isEqualByComparingTo("1000");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(pendingActionRepository.findById(theirs.getId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.PENDING);
    }

    @Test
    void aTransferCannotBeConfirmedWithoutAPin() throws Exception {
        PendingAction own = pendingActionRepository.save(new PendingAction(wanjiku.getId(), null, "1000004821",
                "1000007777", new BigDecimal("500"), "Send KES 500.00 from ...4821 to ...7777", Instant.now().plusSeconds(300)));

        mockMvc.perform(post("/api/v1/assistant/actions/" + own.getId() + "/confirm")
                        .header("Authorization", "Bearer " + wanjikuToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter your PIN to confirm this transfer"));

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(pendingActionRepository.findById(own.getId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.PENDING);
    }

    @Test
    void confirmingAfterExpiryFails() throws Exception {
        PendingAction old = pendingActionRepository.save(freezeRequest(wanjiku, "1000004821", Instant.now().minusSeconds(1)));

        mockMvc.perform(post("/api/v1/assistant/actions/" + old.getId() + "/confirm")
                        .header("Authorization", "Bearer " + wanjikuToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request has expired. Ask the assistant again."));

        assertThat(accountStatus("1000004821")).isEqualTo(AccountStatus.ACTIVE);
        assertThat(pendingActionRepository.findById(old.getId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.EXPIRED);
    }

    @Test
    void chattingInAnotherCustomersConversationReturns404AndNeverReachesTheModel() throws Exception {
        AssistantConversation theirs = conversationRepository.save(new AssistantConversation(otieno.getId(), "Their chat"));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer " + wanjikuToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + theirs.getId() + "\",\"message\":\"What did we talk about?\"}"))
                .andExpect(status().isNotFound());

        verify(chatModel, never()).call(any(Prompt.class));
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(theirs.getId())).isEmpty();
    }

    @Test
    void feedbackOnAnotherCustomersAnswerReturns404() throws Exception {
        AssistantConversation theirs = conversationRepository.save(new AssistantConversation(otieno.getId(), "Their chat"));
        ConversationMessage theirAnswer = messageRepository.save(new ConversationMessage(theirs.getId(),
                "You have KES 5,000.", "v1", "test-model", 10, 5, 100, MessageOutcome.OK));

        mockMvc.perform(post("/api/v1/assistant/feedback")
                        .header("Authorization", "Bearer " + wanjikuToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + theirAnswer.getId() + "\",\"rating\":-1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assistantEndpointsNeedALogin() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/assistant/actions/" + UUID.randomUUID() + "/confirm"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());

        verify(chatModel, never()).call(any(Prompt.class));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private BigDecimal balanceOf(String accountNumber) {
        entityManager.flush();
        entityManager.clear();
        return entityManager
                .createQuery("select a.balance from Account a where a.accountNumber = :n", BigDecimal.class)
                .setParameter("n", accountNumber)
                .getSingleResult();
    }

    private Account account(String number, User owner) {
        return accountRepository.save(new Account(number, new BigDecimal("1000"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", owner, "pin-hash"));
    }

    private PendingAction freezeRequest(User owner, String accountNumber, Instant expiresAt) {
        return new PendingAction(owner.getId(), null, PendingActionType.FREEZE_ACCOUNT, accountNumber,
                "lost phone", "Freeze account ..." + accountNumber.substring(6), expiresAt);
    }

    // Every blocked attempt must leave an audit row. The tools record their row ids in call order.
    private void assertLastToolCall(AssistantTools tools, String toolName, ToolOutcome outcome) {
        List<UUID> ids = tools.recordedCallIds();
        assertThat(ids).isNotEmpty();
        AssistantToolCall last = toolCallRepository.findById(ids.get(ids.size() - 1)).orElseThrow();
        assertThat(last.getToolName()).isEqualTo(toolName);
        assertThat(last.getOutcome()).isEqualTo(outcome);
        assertThat(last.getUserId()).isEqualTo(wanjiku.getId());
    }

    private AccountStatus accountStatus(String accountNumber) {
        entityManager.flush();
        entityManager.clear();
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getStatus();
    }
}
