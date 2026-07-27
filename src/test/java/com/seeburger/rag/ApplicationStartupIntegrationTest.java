package com.seeburger.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationStartupIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("rag")
            .withUsername("rag")
            .withPassword("rag");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.ai.google.genai.embedding.api-key", () -> "test-key");
        registry.add("spring.ai.google.genai.api-key", () -> "test-key");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void startsWithBothProvidersAndRunsFlywayMigration() {
        var migrationCount = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success",
                Integer.class
        );
        var vectorVersion = jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class
        );
        var documentTable = jdbc.queryForObject(
                "SELECT to_regclass('public.documents')::text",
                String.class
        );
        var chunkTable = jdbc.queryForObject(
                "SELECT to_regclass('public.document_chunks')::text",
                String.class
        );

        assertThat(migrationCount).isEqualTo(1);
        assertThat(vectorVersion).isNotBlank();
        assertThat(documentTable).isEqualTo("documents");
        assertThat(chunkTable).isEqualTo("document_chunks");
    }
}
