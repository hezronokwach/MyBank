package com.example.mybank.dto.responses;

import java.math.BigDecimal;

// What a new card costs, so the app shows the fee from the settings
public record CardFeeResponse(
        BigDecimal fee,
        String currency
) {
}
