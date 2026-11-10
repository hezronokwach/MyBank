package com.example.mybank.services;

import com.example.mybank.email.EmailSender;
import com.example.mybank.entity.EmailVerificationToken;
import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.InvalidVerificationLinkException;
import com.example.mybank.repository.EmailVerificationTokenRepository;
import com.example.mybank.services.impl.EmailVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirmation links (issue #40). A fake sender keeps the emails so the test can read the link.
 */
@DataJpaTest(properties = "app.react-url=https://mybank.test")
@Import({EmailVerificationServiceImpl.class, EmailVerificationServiceTest.Beans.class})
class EmailVerificationServiceTest {

    // Keeps every email instead of sending it
    static class FakeEmailSender implements EmailSender {
        final List<String> texts = new ArrayList<>();
        final List<String> recipients = new ArrayList<>();

        @Override
        public void send(String toEmail, String toName, String subject, String text) {
            recipients.add(toEmail);
            texts.add(text);
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        FakeEmailSender emailSender() {
            return new FakeEmailSender();
        }
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EmailVerificationService service;

    @Autowired
    private EmailVerificationTokenRepository tokens;

    @Autowired
    private FakeEmailSender sender;

    private User wanjiku;

    @BeforeEach
    void seed() {
        sender.texts.clear();
        sender.recipients.clear();
        User user = new User("wanjiku@test.local", "hash", "Wanjiku", "Kamau", UserRole.ROLE_USER);
        user.setEmailVerified(false);
        wanjiku = em.persist(user);
        em.flush();
    }

    @Test
    void sendingEmailsALinkAndSavesOnlyTheHash() {
        service.sendVerification(wanjiku);

        assertThat(sender.recipients).containsExactly("wanjiku@test.local");
        String token = tokenFrom(sender.texts.get(0));
        assertThat(sender.texts.get(0)).contains("https://mybank.test/verify-email?token=" + token);

        EmailVerificationToken saved = tokens.findAll().get(0);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(token).doesNotContain(token);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
    }

    @Test
    void theLinkConfirmsTheEmailOnce() {
        service.sendVerification(wanjiku);
        String token = tokenFrom(sender.texts.get(0));

        service.verify(token);

        assertThat(reloadUser().isEmailVerified()).isTrue();
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(InvalidVerificationLinkException.class)
                .hasMessage("This link has expired or was already used");
    }

    @Test
    void anExpiredLinkIsRefused() {
        service.sendVerification(wanjiku);
        String token = tokenFrom(sender.texts.get(0));
        EmailVerificationToken saved = tokens.findAll().get(0);
        saved.setExpiresAt(Instant.now().minusSeconds(1));
        em.flush();

        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(InvalidVerificationLinkException.class);
        assertThat(reloadUser().isEmailVerified()).isFalse();
    }

    @Test
    void aMadeUpTokenIsRefused() {
        assertThatThrownBy(() -> service.verify("not-a-real-token"))
                .isInstanceOf(InvalidVerificationLinkException.class)
                .hasMessage("This link has expired or was already used");
    }

    @Test
    void resendSendsAtMost3LinksAnHour() {
        for (int i = 0; i < 5; i++) {
            service.resend("wanjiku@test.local");
        }

        assertThat(sender.texts).hasSize(3);
    }

    @Test
    void resendDoesNothingForAnUnknownOrAlreadyConfirmedEmail() {
        service.resend("nobody@test.local");

        User confirmed = new User("done@test.local", "hash", "Done", "D", UserRole.ROLE_USER);
        confirmed.setEmailVerified(true);
        em.persist(confirmed);
        em.flush();
        service.resend("done@test.local");

        assertThat(sender.texts).isEmpty();
    }

    @Test
    void anyLinkFromTheLast24HoursWorks() {
        service.sendVerification(wanjiku);
        service.sendVerification(wanjiku);

        // The first email's link still works after a second one was sent
        service.verify(tokenFrom(sender.texts.get(0)));

        assertThat(reloadUser().isEmailVerified()).isTrue();
    }

    @Test
    void olderUsersWithNoValueCountAsConfirmed() {
        User older = em.persist(new User("old@test.local", "hash", "Old", "O", UserRole.ROLE_USER));
        em.flush();
        em.clear();

        assertThat(em.find(User.class, older.getId()).isEmailVerified()).isTrue();
    }

    // ---- helpers

    // The token is everything after "token=" up to the end of that line
    private String tokenFrom(String emailText) {
        int start = emailText.indexOf("token=") + "token=".length();
        int end = emailText.indexOf('\n', start);
        return emailText.substring(start, end);
    }

    private User reloadUser() {
        em.flush();
        em.clear();
        return em.find(User.class, wanjiku.getId());
    }
}
