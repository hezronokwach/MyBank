package com.example.mybank.email;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which email sender the app uses (issue #40).
 */
class EmailConfigTest {

    private final EmailConfig config = new EmailConfig();

    @Test
    void withAKeyItSendsThroughBrevo() {
        EmailSender sender = config.emailSender("brevo-key", "me@example.com", "MyBank", RestClient.builder(), env());

        assertThat(sender).isInstanceOf(BrevoEmailSender.class);
    }

    @Test
    void aKeyWithoutASenderAddressStopsTheApp() {
        assertThatThrownBy(() -> config.emailSender("brevo-key", "", "MyBank", RestClient.builder(), env()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM must be set");
    }

    @Test
    void withoutAKeyOnYourOwnComputerTheEmailIsPrinted() {
        EmailSender sender = config.emailSender("", "", "MyBank", RestClient.builder(), env("dev"));

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void withoutAKeyInProductionItRefusesToSend() {
        // Never print confirmation links in a real server's logs
        EmailSender sender = config.emailSender("", "", "MyBank", RestClient.builder(), env("prod"));

        assertThat(sender).isInstanceOf(NotConfiguredEmailSender.class);
    }

    private MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}
