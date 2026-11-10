package com.example.mybank.controller;

import com.example.mybank.dto.requests.LoginRequest;
import com.example.mybank.dto.requests.RegisterRequest;
import com.example.mybank.dto.requests.ResendVerificationRequest;
import com.example.mybank.dto.requests.VerifyEmailRequest;
import com.example.mybank.dto.responses.AuthResponse;
import com.example.mybank.dto.responses.MessageResponse;
import com.example.mybank.dto.responses.RegisterResponse;
import com.example.mybank.services.EmailVerificationService;
import com.example.mybank.dto.responses.UserResponse;
import com.example.mybank.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(UserService userService, EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        // No login token: the user confirms their email first, then signs in
        RegisterResponse response = userService.registerUser(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    /**
     * POST /api/v1/auth/verify-email
     * The token from the link in the confirmation email. A wrong, used or expired token gets 400.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(new MessageResponse("Your email is confirmed. You can sign in now."));
    }

    /**
     * POST /api/v1/auth/resend-verification
     * Always the same answer, whether or not the email has an account, so it can't be used to find accounts.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.ok(new MessageResponse(
                "If that email is waiting to be confirmed, we sent a new link. Check your inbox."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = userService.loginUser(loginRequest);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/me/role")
    public ResponseEntity<String> getRole(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.getUserRoleByEmail(email));
    }

    /**
     * GET /api/v1/auth/me
     * The signed-in user's own name and email, taken from the JWT. Used to greet them on the Dashboard.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }
}
