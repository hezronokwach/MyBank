package com.example.mybank.config;

import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataLoader {

    // Creates the admin on first start. The admin approves customers' accounts and never has a bank account of its own.
    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail("admin@mybank.com").isEmpty()) {
                User admin = new User(
                        "admin@mybank.com",
                        passwordEncoder.encode("adminpassword"),
                        "Admin",
                        "System",
                        UserRole.ROLE_ADMIN
                );
                userRepository.save(admin);
            }
        };
    }
}
