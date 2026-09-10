package com.example.mybank.config;

import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.util.AccountNumberGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository,
                                   AccountRepository accountRepository,
                                   AccountNumberGenerator accountNumberGenerator,
                                   PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail("admin@mybank.com").isEmpty()) {
                User admin = new User(
                        "admin@mybank.com",
                        passwordEncoder.encode("adminpassword"),
                        "Admin",
                        "System",
                        UserRole.ROLE_ADMIN
                );
                User savedUser = userRepository.save(admin);
            }
        };
    }
}
