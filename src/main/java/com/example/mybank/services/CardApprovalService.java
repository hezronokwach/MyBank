package com.example.mybank.services;

import com.example.mybank.dto.responses.AdminCardResponse;
import com.example.mybank.enums.CardStatus;

import java.util.List;
import java.util.UUID;

// What the admin does with card applications
public interface CardApprovalService {
    // status null means every card
    List<AdminCardResponse> listCards(CardStatus status);

    AdminCardResponse approve(String adminEmail, UUID cardId);

    AdminCardResponse reject(String adminEmail, UUID cardId, String reason);
}
