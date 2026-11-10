package com.example.mybank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Checks that the whole app starts. It uses its own in-memory database, so a test run never touches
// the real database in application-dev.properties (for example Neon).
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-loads;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MyBankApplicationTests {

    @Test
    void contextLoads() {
    }

}
