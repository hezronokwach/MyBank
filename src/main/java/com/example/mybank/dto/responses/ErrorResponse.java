package com.example.mybank.dto.responses;

import java.time.LocalDateTime;

public record ErrorResponse(
        LocalDateTime timeStamp,
        int status,
        
) {
}
