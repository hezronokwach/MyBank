package com.example.mybank.dto.requests;

import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;

public record AccountStatusUpdateRequest( //for the admins
                                          AccountStatus status,
                                          AccountTier tier
) {
}
