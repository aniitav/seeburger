package com.seeburger.rag.vector;

import com.seeburger.rag.chunking.DocumentChunk;
import com.seeburger.rag.common.Hashing;
import com.seeburger.rag.config.RagProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("rag")
            .withUsername("rag")
            .withPassword("rag");

    private PgVectorRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("TRUNCATE document_chunks, documents CASCADE");
        repository = new PgVectorRepository(jdbc, properties());
    }

    @Test
    void storesAndRetrievesTheNearestChunk() {
        var documentId = UUID.randomUUID();
        var chunk = new DocumentChunk(
                UUID.randomUUID(), 0, 1, 1, "Leave",
                "Employees receive twenty days of annual leave.",
                Hashing.sha256("Employees receive twenty days of annual leave.")
        );
        var vector = unitVector(0);
        repository.store(
                documentId, "handbook.txt", "text/plain", Hashing.sha256("handbook"),
                List.of(chunk), List.of(vector)
        );

        var results = repository.search(vector, documentId, 5, 0.65);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunk.id());
        assertThat(results.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    private float[] unitVector(int index) {
        var vector = new float[1536];
        vector[index] = 1;
        return vector;
    }

    private RagProperties properties() {
        return new RagProperties(
                new RagProperties.Upload(10_000, 100_000),
                new RagProperties.Chunk(800, 100, 40),
                new RagProperties.Retrieval(5, 0.65),
                new RagProperties.Context(4000),
                new RagProperties.Ai(
                        "openai", "text-embedding-3-small", 1536, 100,
                        "openai", "gpt-5.6-luna", 1000
                )
        );
    }
}
