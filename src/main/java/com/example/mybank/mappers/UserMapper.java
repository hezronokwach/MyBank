package com.example.mybank.mappers;

import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.responses.AccountSummaryResponse;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.dto.responses.UserResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request, String hashedPassword) {
        UserRole role = request.role() != null ? request.role() : UserRole.ROLE_USER;

        // Order matched to User constructor: (email, passwordHash, firstName, lastName, role)
        return new User(
                request.email(),
                hashedPassword,
                request.firstName(),
                request.lastName(),
                role
        );
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt()
        );
    }

    public AuthResponse toAuthResponse(String token, long expirationMs, User user, Account account) {
        AccountSummaryResponse accountSummaryResponse = null;
        if (account != null) {
            accountSummaryResponse = new AccountSummaryResponse(
                    account.getAccountNumber(),
                    account.getBalance(),
                    account.getCurrency(),
                    account.getType(),
                    account.getStatus(),
                    account.getTier()
            );
        }
        return new AuthResponse(
                token,
                expirationMs,
                user.getEmail(),
                accountSummaryResponse
        );
    }
}