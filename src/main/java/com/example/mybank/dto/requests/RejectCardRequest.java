package com.example.mybank.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /api/v1/admin/cards/{id}/reject: the reason is shown to the customer
public record RejectCardRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 200, message = "The reason must be at most 200 characters")
        String reason
) {
}
