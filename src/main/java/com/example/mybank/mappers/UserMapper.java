package com.example.mybank.mappers;

import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.dto.responses.UserResponse;
import com.example.mybank.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    /*
      Converts a RegisterRequest DTO to a User Entity.
      Note: Expects the password to already be encoded before passing into this method,
      or passed as the encoded hash parameter.
     */
    public User toEntity(RegisterRequest registerRequest, String encodedPassword) {
        return new User(
                registerRequest.email(),
                encodedPassword,
                registerRequest.firstName(),
                registerRequest.lastName()
        );
    }
    /*
      Converts a User Entity into a safe UserResponse DTO.
      Strips away internal data like password hash.
     */
    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt()
        );
    }

    public AuthResponse toAuthResponse(String token,long expirationMs, User user) {
        UserResponse userResponse = toUserResponse(user);
        return new AuthResponse(
                token,
                "Bearer",
                expirationMs,
                userResponse
        );
    }
}
