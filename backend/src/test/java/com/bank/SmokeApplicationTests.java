package com.bank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 0 smoke test: boots the full Spring context against a real PostgreSQL
 * container (via Testcontainers + @ServiceConnection) so Flyway runs the V1
 * baseline migration. If this passes, the core wiring is sound.
 */
@SpringBootTest
@Testcontainers
class SmokeApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Context startup (incl. Flyway migration) is the assertion.
    }
}
