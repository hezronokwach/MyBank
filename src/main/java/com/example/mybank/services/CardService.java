package com.example.mybank.services;

import com.example.mybank.dto.responses.CardFeeResponse;
import com.example.mybank.dto.responses.CardResponse;

import java.util.List;

public interface CardService {
    CardResponse applyForCard(String email, String accountNumber, String pin);

    List<CardResponse> getMyCards(String email);

    CardFeeResponse getIssueFee();
}
