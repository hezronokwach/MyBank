package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.AssistantMetricsSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/assistant")
public class AssistantAdminController {

    private final AssistantMetrics metrics;

    public AssistantAdminController(AssistantMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * GET /api/v1/admin/assistant/metrics
     * The assistant's numbers for the Admin Dashboard, in one call. The same numbers are at /actuator/metrics.
     * Only totals: no messages, account numbers or customer details.
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssistantMetricsSummary> getMetrics() {
        return ResponseEntity.ok(metrics.summary());
    }
}
