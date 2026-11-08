package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.FeedbackResponse;
import com.example.mybank.assistant.entity.AssistantFeedback;
import com.example.mybank.assistant.entity.ConversationMessage;
import com.example.mybank.assistant.entity.MessageRole;
import com.example.mybank.assistant.repository.AssistantConversationRepository;
import com.example.mybank.assistant.repository.AssistantFeedbackRepository;
import com.example.mybank.assistant.repository.ConversationMessageRepository;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.InvalidFeedbackException;
import com.example.mybank.exceptions.MessageNotFoundException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Thumbs up / thumbs down on the assistant's answers: a real-world quality signal,
 * linked to the audit trail through the message id.
 */
@Service
public class FeedbackService {

    private final UserRepository userRepository;
    private final ConversationMessageRepository messageRepository;
    private final AssistantConversationRepository conversationRepository;
    private final AssistantFeedbackRepository feedbackRepository;
    private final OutputGuard outputGuard;
    private final AssistantMetrics metrics;

    public FeedbackService(UserRepository userRepository,
                           ConversationMessageRepository messageRepository,
                           AssistantConversationRepository conversationRepository,
                           AssistantFeedbackRepository feedbackRepository,
                           OutputGuard outputGuard,
                           AssistantMetrics metrics) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.feedbackRepository = feedbackRepository;
        this.outputGuard = outputGuard;
        this.metrics = metrics;
    }

    @Transactional
    public FeedbackResponse giveFeedback(String email, UUID messageId, int rating, String comment) {
        // 1. Only thumbs up (1) or thumbs down (-1)
        if (rating != 1 && rating != -1) {
            throw new InvalidFeedbackException("Rating must be 1 or -1");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        // 2. The message must exist AND be in one of this customer's conversations.
        //    Someone else's message gives the same answer as a message that doesn't exist.
        ConversationMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message " + messageId + " not found"));

        boolean ownConversation = conversationRepository
                .findByIdAndUserId(message.getConversationId(), user.getId())
                .isPresent();
        if (!ownConversation) {
            throw new MessageNotFoundException("Message " + messageId + " not found");
        }

        // 3. Only the assistant's answers can be rated, not the customer's own questions
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new InvalidFeedbackException("Only the assistant's answers can be rated");
        }

        // 4. Clean the comment: empty becomes nothing, and a PIN typed into it is never stored
        String cleanComment = null;
        if (comment != null && !comment.isBlank()) {
            cleanComment = outputGuard.clean(comment.trim());
        }

        // 5. Save it, replacing an earlier rating of the same answer
        AssistantFeedback feedback;
        Optional<AssistantFeedback> earlier = feedbackRepository.findByMessageIdAndUserId(messageId, user.getId());
        if (earlier.isPresent()) {
            feedback = earlier.get();
            feedback.setRating(rating);
            feedback.setComment(cleanComment);
        } else {
            feedback = new AssistantFeedback(messageId, user.getId(), rating, cleanComment);
        }

        AssistantFeedback saved = feedbackRepository.save(feedback);
        metrics.recordFeedback(rating);
        return new FeedbackResponse(saved.getId(), saved.getMessageId(), saved.getRating(), saved.getComment());
    }
}
