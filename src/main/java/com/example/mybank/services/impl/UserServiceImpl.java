package com.example.mybank.services.impl;

import com.example.mybank.dto.requests.LoginRequest;
import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.DuplicateResourceException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.mappers.UserMapper;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import com.example.mybank.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper, JwtTokenProvider jwtTokenProvider, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional
    public AuthResponse registerUser(RegisterRequest registerRequest) {
        if(userRepository.existsByEmail(registerRequest.email())){
            throw new DuplicateResourceException("Email already exists" + registerRequest.email());
        }
        String hashedPassword = passwordEncoder.encode(registerRequest.password());
        User userEntity = userMapper.toEntity(registerRequest, hashedPassword);
        User savedUser = userRepository.save(userEntity);
        String token = jwtTokenProvider.generateToken(savedUser.getEmail());
        return userMapper.toAuthResponse(token,jwtExpirationMs, savedUser);
    }

    @Override
    public AuthResponse loginUser(LoginRequest loginRequest) {
      try {
          authenticationManager.authenticate(
                  new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
          );
      } catch (AuthenticationException e) {
          throw new UnauthorizedException("Invalid email or password");

      }


      User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(()-> (new UserNotFoundException("User email not found" + loginRequest.email())));
      String token = jwtTokenProvider.generateToken(user.getEmail());
      return userMapper.toAuthResponse(token,jwtExpirationMs, user);
    }
}
