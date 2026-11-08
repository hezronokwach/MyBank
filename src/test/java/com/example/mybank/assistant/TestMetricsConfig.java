package com.example.mybank.assistant;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * For @DataJpaTest tests: they only start the database part of the app, so there is no metrics
 * registry. This gives AssistantMetrics a simple in-memory one.
 * Use it together with AssistantMetrics.class in @Import.
 */
@TestConfiguration
public class TestMetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
