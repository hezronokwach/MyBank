package com.example.mybank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   CustomUserDetailsService customUserDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract token from the "Authorization" header
        String token = parseBearerToken(request);

        // 2. Validate token and ensure user isn't already authenticated for this request thread
        if (StringUtils.hasText(token)
                && jwtTokenProvider.validateToken(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            // 3. Extract email from token payload ("sub" claim)
            String email = jwtTokenProvider.extractEmail(token);

            // 4. Load UserDetails from DB using CustomUserDetailsService
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

            // 5. Create Spring Security Authentication object
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null, // Credentials are set to null because token is already verified
                    userDetails.getAuthorities()
            );

            // Attach IP/session request details to authentication
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 6. Set Authentication into SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 7. Continue downstream filter chain execution
        filterChain.doFilter(request, response);
    }

    // Helper method to extract "Bearer <token>"
    private String parseBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Removes "Bearer " prefix (7 characters)
        }
        return null;
    }
}