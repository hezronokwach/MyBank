package com.example.mybank.dto.responses;

// The full card details, shown only after the right PIN. Sent with Cache-Control: no-store.
public record CardDetailsResponse(
        String cardNumber,
        String cvv,
        Integer expiryMonth,
        Integer expiryYear,
        String last4
) {
    // Never print the number or CVV, for example if this object ends up in a log line
    @Override
    public String toString() {
        return "CardDetailsResponse[cardNumber=**** **** **** " + last4 + ", cvv=***]";
    }
}
