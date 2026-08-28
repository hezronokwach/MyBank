package com.example.mybank.services;

import com.example.mybank.dto.requests.LoginRequest;
import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.entity.User;
import org.springframework.stereotype.Service;

@Service
public interface UserService {
    AuthResponse registerUser(RegisterRequest registerRequest);
    AuthResponse loginUser(LoginRequest loginRequest);
}
