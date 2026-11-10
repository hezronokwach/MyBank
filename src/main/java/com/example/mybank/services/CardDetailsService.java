package com.example.mybank.services;

import com.example.mybank.dto.responses.CardDetailsResponse;

import java.util.UUID;

public interface CardDetailsService {
    CardDetailsResponse revealDetails(String email, UUID cardId, String pin);
}
