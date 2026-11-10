package com.example.mybank.services.impl;

import com.example.mybank.dto.requests.LoginRequest;
import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.DuplicateResourceException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.dto.responses.RegisterResponse;
import com.example.mybank.dto.responses.UserResponse;
import com.example.mybank.exceptions.EmailNotVerifiedException;
import com.example.mybank.services.EmailVerificationService;
import com.example.mybank.mappers.UserMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import com.example.mybank.services.UserService;
import com.example.mybank.util.AccountNumberGenerator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper, JwtTokenProvider jwtTokenProvider, AuthenticationManager authenticationManager, AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator, EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.emailVerificationService = emailVerificationService;
    }

    @Override
    @Transactional
    public RegisterResponse registerUser(RegisterRequest registerRequest) {
        if(userRepository.existsByEmail(registerRequest.email())){
            throw new DuplicateResourceException("Email already exists" + registerRequest.email());
        }
        String hashedPassword = passwordEncoder.encode(registerRequest.password());
        String hashedPin = passwordEncoder.encode(registerRequest.pin());
        User userEntity = userMapper.toEntity(registerRequest, hashedPassword);
        // New users must confirm their email before they can sign in
        userEntity.setEmailVerified(false);
        User savedUser = userRepository.save(userEntity);
        Account account = new Account (
                accountNumberGenerator.generate(),
                BigDecimal.ZERO,
                AccountStatus.PENDING_VERIFICATION,
                AccountTier.TIER_1_UNVERIFIED,
                AccountType.CURRENT,
                "KES",
                savedUser,
                hashedPin
        );
        accountRepository.save(account);

        // Send the confirmation link. If the email can't be sent, this throws and nothing above is saved,
        // so the user can simply try again.
        emailVerificationService.sendVerification(savedUser);

        return new RegisterResponse(savedUser.getEmail(),
                "We sent a link to " + savedUser.getEmail() + ". Open it to confirm your email, then sign in.");
    }

    @Override
    @Transactional(readOnly = true)
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

      // Checked after the password, so this answer never tells a stranger whether an email is registered
      if (!user.isEmailVerified()) {
          throw new EmailNotVerifiedException("Confirm your email first. Check your inbox for the link.");
      }
      Account account = null;
      if (user.getRole() != UserRole.ROLE_ADMIN) {
          List<Account> accounts = accountRepository.findByUserId(user.getId());
          if (accounts.isEmpty()) {
              throw new UserNotFoundException("Account not found" + loginRequest.email());
          }
          account = accounts.get(0);
      }

      String token = jwtTokenProvider.generateToken(user);
      return userMapper.toAuthResponse(token,jwtExpirationMs, user,account);
    }
    @Override
    @Transactional(readOnly = true)
    public String getUserRoleByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return user.getRole().name();
    }

    // The signed-in user's own details, for example to greet them by name. No password or PIN.
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return userMapper.toUserResponse(user);
    }
}
