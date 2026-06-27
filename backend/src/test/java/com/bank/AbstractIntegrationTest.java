package com.bank;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for integration tests: real Postgres + Redis via Testcontainers,
 * wired through Spring Boot's @ServiceConnection.
 *
 * <p>Uses the <b>singleton container</b> pattern — containers are started once in
 * a static initializer and never stopped, so their mapped ports stay stable for
 * the whole JVM. This keeps Spring's cached test context valid across multiple
 * test classes (a managed @Testcontainers lifecycle would stop the containers
 * after the first class and leave the cached context pointing at a dead port).
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    static {
        postgres.start();
        redis.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "test-secret-0123456789-0123456789-banking-system-key");
        // Disable per-IP auth rate limiting so the many register/login calls across the
        // suite (all from 127.0.0.1) don't trip it. The filter is unit-tested separately.
        registry.add("app.ratelimit.enabled", () -> "false");
    }
}
