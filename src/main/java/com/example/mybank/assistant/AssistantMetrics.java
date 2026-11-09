package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.AssistantMetricsSummary;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.assistant.entity.PendingActionStatus;
import com.example.mybank.assistant.entity.PendingActionType;
import com.example.mybank.assistant.entity.ToolOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * All the assistant's numbers, in one place. They show up at /actuator/metrics (admins only), for example
 * /actuator/metrics/assistant.requests?tag=outcome:OK
 *
 * Each metric has a name and one or more tags. A tag splits the number into groups:
 * assistant.requests with tag outcome=OK counts only the answers that worked.
 *
 *   assistant.requests        answers given            tag: outcome (OK, FALLBACK, RATE_LIMITED, RATE_LIMITED_PROVIDER)
 *   assistant.latency         how long the model took  tag: outcome
 *   assistant.tokens          tokens used              tag: type (input, output)
 *   assistant.cost.estimate   what the tokens would cost on a paid plan, in US dollars
 *   assistant.tool.calls      tool calls               tags: tool, outcome
 *   assistant.blocked         things that were stopped tag: reason (rate_limit, tool_limit, output_guard)
 *   assistant.actions         confirm cards            tags: type, status (PENDING, CONFIRMED, CANCELLED, EXPIRED, FAILED)
 *   assistant.feedback        thumbs up / down         tag: rating (up, down)
 *
 * The numbers live in memory: they start again from 0 when the app restarts.
 * No message text, account number or user id is ever put in a tag.
 */
@Component
public class AssistantMetrics {

    public static final String BLOCKED_RATE_LIMIT = "rate_limit";
    public static final String BLOCKED_TOOL_LIMIT = "tool_limit";
    public static final String BLOCKED_OUTPUT_GUARD = "output_guard";

    private static final double ONE_MILLION = 1_000_000.0;

    private final MeterRegistry registry;
    private final double inputPricePerMillion;
    private final double outputPricePerMillion;
    private final Instant countingSince = Instant.now();

    public AssistantMetrics(MeterRegistry registry,
                            @Value("${app.assistant.price.input-per-million:0.15}") double inputPricePerMillion,
                            @Value("${app.assistant.price.output-per-million:0.60}") double outputPricePerMillion) {
        this.registry = registry;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    // One answer: its outcome, how long it took, and the tokens it used (null when the model didn't answer)
    public void recordAnswer(MessageOutcome outcome, long latencyMs, Integer inputTokens, Integer outputTokens) {
        counter("assistant.requests", "outcome", outcome.name()).increment();

        Timer latency = Timer.builder("assistant.latency")
                .description("Time spent waiting for the model")
                .tag("outcome", outcome.name())
                .register(registry);
        latency.record(Duration.ofMillis(latencyMs));

        double cost = 0;
        if (inputTokens != null) {
            counter("assistant.tokens", "type", "input").increment(inputTokens);
            cost = cost + inputTokens * inputPricePerMillion / ONE_MILLION;
        }
        if (outputTokens != null) {
            counter("assistant.tokens", "type", "output").increment(outputTokens);
            cost = cost + outputTokens * outputPricePerMillion / ONE_MILLION;
        }

        Counter costCounter = Counter.builder("assistant.cost.estimate")
                .description("Estimated model cost on a paid plan")
                .baseUnit("usd")
                .register(registry);
        costCounter.increment(cost);
    }

    // A message the rate limiter turned away: the model was never asked
    public void recordRateLimited() {
        counter("assistant.requests", "outcome", MessageOutcome.RATE_LIMITED.name()).increment();
        recordBlocked(BLOCKED_RATE_LIMIT);
    }

    public void recordToolCall(String toolName, ToolOutcome outcome) {
        Counter toolCalls = Counter.builder("assistant.tool.calls")
                .tag("tool", toolName)
                .tag("outcome", outcome.name())
                .register(registry);
        toolCalls.increment();
    }

    // reason is one of the BLOCKED_ names above
    public void recordBlocked(String reason) {
        counter("assistant.blocked", "reason", reason).increment();
    }

    // A confirm card was created (PENDING) or decided (CONFIRMED, CANCELLED, EXPIRED, FAILED)
    public void recordAction(PendingActionType type, PendingActionStatus status) {
        Counter actions = Counter.builder("assistant.actions")
                .tag("type", type.name())
                .tag("status", status.name())
                .register(registry);
        actions.increment();
    }

    // rating is 1 (thumbs up) or -1 (thumbs down)
    public void recordFeedback(int rating) {
        String name;
        if (rating > 0) {
            name = "up";
        } else {
            name = "down";
        }
        counter("assistant.feedback", "rating", name).increment();
    }

    // ---- Reading the numbers back, for the Admin Dashboard

    public AssistantMetricsSummary summary() {
        // Average time of the answers that worked
        long averageLatencyMs = 0;
        Timer okLatency = registry.find("assistant.latency").tag("outcome", MessageOutcome.OK.name()).timer();
        if (okLatency != null && okLatency.count() > 0) {
            averageLatencyMs = Math.round(okLatency.mean(TimeUnit.MILLISECONDS));
        }

        long toolCalls = total("assistant.tool.calls", null, null);
        long toolCallsOk = total("assistant.tool.calls", "outcome", ToolOutcome.OK.name());

        double cost = 0;
        Counter costCounter = registry.find("assistant.cost.estimate").counter();
        if (costCounter != null) {
            cost = costCounter.count();
        }

        return new AssistantMetricsSummary(
                countingSince,
                total("assistant.requests", "outcome", MessageOutcome.OK.name()),
                total("assistant.requests", "outcome", MessageOutcome.FALLBACK.name()),
                total("assistant.requests", "outcome", MessageOutcome.RATE_LIMITED_PROVIDER.name()),
                total("assistant.requests", "outcome", MessageOutcome.RATE_LIMITED.name()),
                averageLatencyMs,
                total("assistant.tokens", "type", "input"),
                total("assistant.tokens", "type", "output"),
                cost,
                toolCalls,
                toolCalls - toolCallsOk,
                total("assistant.blocked", "reason", BLOCKED_RATE_LIMIT),
                total("assistant.blocked", "reason", BLOCKED_TOOL_LIMIT),
                total("assistant.blocked", "reason", BLOCKED_OUTPUT_GUARD),
                total("assistant.actions", "status", PendingActionStatus.PENDING.name()),
                total("assistant.actions", "status", PendingActionStatus.CONFIRMED.name()),
                total("assistant.actions", "status", PendingActionStatus.CANCELLED.name()),
                total("assistant.actions", "status", PendingActionStatus.EXPIRED.name()),
                total("assistant.actions", "status", PendingActionStatus.FAILED.name()),
                total("assistant.feedback", "rating", "up"),
                total("assistant.feedback", "rating", "down"));
    }

    // Adds up every counter with this name and tag. For example assistant.tool.calls with outcome=OK
    // has one counter per tool, so they are added together. Pass null as tagName to add up all of them.
    private long total(String name, String tagName, String tagValue) {
        double sum = 0;
        for (Counter counter : registry.find(name).counters()) {
            if (tagName == null || tagValue.equals(counter.getId().getTag(tagName))) {
                sum = sum + counter.count();
            }
        }
        return Math.round(sum);
    }

    // A counter with one tag. register() gives back the same counter every time for the same name and tag.
    private Counter counter(String name, String tagName, String tagValue) {
        return Counter.builder(name)
                .tag(tagName, tagValue)
                .register(registry);
    }
}
