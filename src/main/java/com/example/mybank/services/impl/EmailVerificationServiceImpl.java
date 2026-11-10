package com.example.mybank.services.impl;

import com.example.mybank.email.EmailSender;
import com.example.mybank.entity.EmailVerificationToken;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.EmailNotSentException;
import com.example.mybank.exceptions.InvalidVerificationLinkException;
import com.example.mybank.repository.EmailVerificationTokenRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.EmailVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Confirmation links for new sign-ups.
 *
 *   - The token is 32 random bytes. The link holds the token; the database only holds its SHA-256 hash.
 *   - A link works once and expires after 24 hours.
 *   - Resend sends at most 3 links an hour per user, and answers the same way whether the email exists or not,
 *     so it can't be used to find out who has an account.
 */
@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

    static final Duration LINK_VALID_FOR = Duration.ofHours(24);
    static final int MAX_LINKS_PER_HOUR = 3;
    static final int TOKEN_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationServiceImpl.class);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationServiceImpl(EmailVerificationTokenRepository tokenRepository,
                                        UserRepository userRepository,
                                        EmailSender emailSender,
                                        @Value("${FRONTEND_URL:${app.react-url:http://localhost:5173}}") String frontendUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.frontendUrl = frontendUrl;
    }

    @Override
    @Transactional
    public void sendVerification(User user) {
        // 1. A random token, made safe for a URL
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // 2. Save only its hash
        tokenRepository.save(new EmailVerificationToken(user.getId(), hash(token), Instant.now().plus(LINK_VALID_FOR)));

        // 3. Email the link
        String link = frontendUrl + "/verify-email?token=" + token;
        String text = "Hi " + user.getFirstName() + ",\n\n"
                + "Thanks for signing up to MyBank. Please confirm your email address by opening this link:\n\n"
                + link + "\n\n"
                + "The link works once and expires in 24 hours. After you confirm, an admin will approve your account.\n\n"
                + "If you didn't sign up, you can ignore this email.\n\n"
                + "MyBank";
        emailSender.send(user.getEmail(), user.getFirstName() + " " + user.getLastName(), "Confirm your MyBank email", text);
    }

    @Override
    @Transactional
    public void verify(String token) {
        // Unknown, already used and expired links all get the same answer
        EmailVerificationToken saved = tokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new InvalidVerificationLinkException("This link has expired or was already used"));
        if (saved.getUsedAt() != null || Instant.now().isAfter(saved.getExpiresAt())) {
            throw new InvalidVerificationLinkException("This link has expired or was already used");
        }

        User user = userRepository.findById(saved.getUserId())
                .orElseThrow(() -> new InvalidVerificationLinkException("This link has expired or was already used"));
        user.setEmailVerified(true);
        userRepository.save(user);
        saved.setUsedAt(Instant.now());
        tokenRepository.save(saved);
    }

    @Override
    @Transactional
    public void resend(String email) {
        Optional<User> found = userRepository.findByEmail(email.trim());
        // Nothing to do for an unknown email or someone already confirmed. The caller answers the same either way.
        if (found.isEmpty() || found.get().isEmailVerified()) {
            return;
        }
        User user = found.get();

        long sentInLastHour = tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), Instant.now().minus(Duration.ofHours(1)));
        if (sentInLastHour >= MAX_LINKS_PER_HOUR) {
            log.info("Not resending a confirmation link to user {}: {} already sent this hour", user.getId(), sentInLastHour);
            return;
        }

        try {
            sendVerification(user);
        } catch (EmailNotSentException e) {
            // Still the same answer to the caller; the reason is in the log
            log.warn("Could not resend a confirmation link to user {}", user.getId());
        }
    }

    // SHA-256 of the token, as 64 hex characters
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
