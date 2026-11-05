package com.example.mybank.assistant;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows each customer 10 assistant messages per minute.
 *
 * It remembers when each customer's recent messages arrived, in memory. That is enough while MyBank
 * runs as one server; with several servers this would move to a shared store such as Redis.
 */
@Component
public class RateLimiter {

    static final int MAX_MESSAGES = 10;
    static final long WINDOW_MS = 60_000; // one minute

    // email -> times (in milliseconds) of that customer's messages in the last minute
    private final Map<String, List<Long>> recentMessages = new HashMap<>();

    /**
     * Returns true and counts the message if the customer is under the limit, false if they must wait.
     * synchronized: two requests from the same customer can arrive at the same moment.
     */
    public synchronized boolean tryAcquire(String email) {
        long now = System.currentTimeMillis();

        List<Long> times = recentMessages.get(email);
        if (times == null) {
            times = new ArrayList<>();
            recentMessages.put(email, times);
        }

        // Forget messages older than one minute
        List<Long> stillRecent = new ArrayList<>();
        for (Long time : times) {
            if (now - time < WINDOW_MS) {
                stillRecent.add(time);
            }
        }
        times.clear();
        times.addAll(stillRecent);

        if (times.size() >= MAX_MESSAGES) {
            return false;
        }

        times.add(now);
        return true;
    }
}
