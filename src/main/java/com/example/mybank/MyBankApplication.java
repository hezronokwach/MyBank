package com.example.mybank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Runs @Scheduled jobs, such as expiring the assistant's pending actions every minute
public class MyBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyBankApplication.class, args);
    }

}
