package com.example.mybank.services.impl;

import com.example.mybank.dto.requests.LoginRequest;
import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.exceptions.DuplicateResourceException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.mappers.UserMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import com.example.mybank.services.UserService;
import com.example.mybank.util.AccountNumberGenerator;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper, JwtTokenProvider jwtTokenProvider, AuthenticationManager authenticationManager, AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
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
        Account account = new Account (
                accountNumberGenerator.generate(),
                BigDecimal.ZERO,
                AccountStatus.PENDING_VERIFICATION,
                AccountTier.TIER_1_UNVERIFIED,
                AccountType.CURRENT,
                "KES",
                savedUser
        );
        Account savedAccount = accountRepository.save(account);

        String token = jwtTokenProvider.generateToken(savedUser.getEmail());
        return userMapper.toAuthResponse(token,jwtExpirationMs, savedUser, savedAccount);
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
      Account account = accountRepository.findById(user.getId())
              .orElseThrow(()-> (new UserNotFoundException("Account not found" + loginRequest.email())));

      String token = jwtTokenProvider.generateToken(user.getEmail());
      return userMapper.toAuthResponse(token,jwtExpirationMs, user,account);
    }
}
