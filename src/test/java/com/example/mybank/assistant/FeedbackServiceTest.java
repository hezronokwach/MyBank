package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.FeedbackResponse;
import com.example.mybank.assistant.entity.AssistantConversation;
import com.example.mybank.assistant.entity.AssistantFeedback;
import com.example.mybank.assistant.entity.ConversationMessage;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.assistant.entity.MessageRole;
import com.example.mybank.assistant.repository.AssistantConversationRepository;
import com.example.mybank.assistant.repository.AssistantFeedbackRepository;
import com.example.mybank.assistant.repository.ConversationMessageRepository;
import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.InvalidFeedbackException;
import com.example.mybank.exceptions.MessageNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({FeedbackService.class, OutputGuard.class, AssistantMetrics.class, TestMetricsConfig.class})
class FeedbackServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String OTIENO = "otieno@test.local";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private FeedbackService service;

    @Autowired
    private AssistantConversationRepository conversations;

    @Autowired
    private ConversationMessageRepository messages;

    @Autowired
    private AssistantFeedbackRepository feedback;

    @Autowired
    private MeterRegistry meterRegistry;

    private ConversationMessage wanjikuQuestion;
    private ConversationMessage wanjikuAnswer;
    private ConversationMessage otienoAnswer;

    @BeforeEach
    void seed() {
        User wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        User otieno = em.persist(new User(OTIENO, "hash", "Otieno", "O", UserRole.ROLE_USER));

        AssistantConversation wanjikuChat = conversations.save(new AssistantConversation(wanjiku.getId(), "Balance"));
        wanjikuQuestion = messages.save(new ConversationMessage(wanjikuChat.getId(), MessageRole.USER, "How much do I have?"));
        wanjikuAnswer = messages.save(answer(wanjikuChat.getId(), "You have KES 1,500 in ...4821."));

        AssistantConversation otienoChat = conversations.save(new AssistantConversation(otieno.getId(), "Balance"));
        otienoAnswer = messages.save(answer(otienoChat.getId(), "You have KES 99,999 in ...7777."));
    }

    @Test
    void savesAThumbsUpWithAComment() {
        FeedbackResponse response = service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), 1, "  Quick and clear  ");

        assertThat(response.rating()).isEqualTo(1);
        assertThat(response.comment()).isEqualTo("Quick and clear");

        AssistantFeedback saved = feedback.findById(response.feedbackId()).orElseThrow();
        assertThat(saved.getMessageId()).isEqualTo(wanjikuAnswer.getId());
        assertThat(saved.getRating()).isEqualTo(1);
    }

    @Test
    void ratingTheSameAnswerAgainReplacesTheOldRating() {
        service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), 1, "Good");
        service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), -1, null);

        List<AssistantFeedback> all = feedback.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getRating()).isEqualTo(-1);
        assertThat(all.get(0).getComment()).isNull();
    }

    @Test
    void thumbsUpAndDownAreCounted() {
        double upBefore = count("up");
        double downBefore = count("down");

        service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), 1, null);
        service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), -1, null);

        assertThat(count("up")).isEqualTo(upBefore + 1);
        assertThat(count("down")).isEqualTo(downBefore + 1);
    }

    @Test
    void feedbackOnAnotherCustomersAnswerIsRejected() {
        assertThatThrownBy(() -> service.giveFeedback(WANJIKU, otienoAnswer.getId(), -1, "bad"))
                .isInstanceOf(MessageNotFoundException.class)
                .hasMessage("Message " + otienoAnswer.getId() + " not found");

        assertThat(feedback.count()).isZero();
    }

    @Test
    void unknownMessageGivesTheSameError() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.giveFeedback(WANJIKU, unknown, 1, null))
                .isInstanceOf(MessageNotFoundException.class)
                .hasMessage("Message " + unknown + " not found");
    }

    @Test
    void onlyTheAssistantsAnswersCanBeRated() {
        assertThatThrownBy(() -> service.giveFeedback(WANJIKU, wanjikuQuestion.getId(), 1, null))
                .isInstanceOf(InvalidFeedbackException.class)
                .hasMessage("Only the assistant's answers can be rated");
    }

    @Test
    void ratingMustBeUpOrDown() {
        assertThatThrownBy(() -> service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), 0, null))
                .isInstanceOf(InvalidFeedbackException.class)
                .hasMessage("Rating must be 1 or -1");
    }

    @Test
    void aPinTypedIntoTheCommentIsNotStored() {
        FeedbackResponse response = service.giveFeedback(WANJIKU, wanjikuAnswer.getId(), -1,
                "It asked nothing, but my PIN is 1234 if needed");

        assertThat(response.comment()).isEqualTo("It asked nothing, but my PIN is **** if needed");
    }

    private ConversationMessage answer(UUID conversationId, String text) {
        return new ConversationMessage(conversationId, text, "v1", "test-model", 100, 20, 500, MessageOutcome.OK);
    }

    // How many thumbs "up" or "down" were counted, or 0 if none yet
    private double count(String rating) {
        Counter counter = meterRegistry.find("assistant.feedback").tag("rating", rating).counter();
        if (counter == null) {
            return 0;
        }
        return counter.count();
    }
}
