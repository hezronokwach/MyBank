package com.example.mybank.dto.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull(message = "New owner email is required")
        @Email(message = "Invalid email format")
        String newOwnerEmail,
        
        @NotNull(message = "PIN is required")
        String pin
) {}
