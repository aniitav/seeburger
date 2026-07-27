package com.seeburger.rag.vector;

import com.seeburger.rag.chunking.DocumentChunk;
import com.seeburger.rag.config.RagProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PgVectorRepository implements VectorRepository {
    private final JdbcTemplate jdbc;
    private final RagProperties properties;

    public PgVectorRepository(JdbcTemplate jdbc, RagProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public Optional<IndexedDocument> findIndexedByContentHash(String contentHash) {
        var sql = """
                SELECT id, file_name, content_type, content_sha256, chunks_count, uploaded_at
                FROM documents
                WHERE content_sha256 = ?
                  AND embedding_provider = ?
                  AND embedding_model = ?
                  AND embedding_dimensions = ?
                  AND status = 'INDEXED'
                """;
        var result = jdbc.query(
                sql,
                this::mapDocument,
                contentHash,
                properties.ai().embeddingProvider(),
                properties.ai().embeddingModel(),
                properties.ai().embeddingDimensions()
        );
        return result.stream().findFirst();
    }

    @Override
    @Transactional
    public IndexedDocument store(
            UUID documentId,
            String fileName,
            String contentType,
            String contentHash,
            List<DocumentChunk> chunks,
            List<float[]> embeddings
    ) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Every chunk must have exactly one embedding");
        }
        var ai = properties.ai();
        jdbc.update("""
                        INSERT INTO documents (
                            id, file_name, content_type, content_sha256,
                            embedding_provider, embedding_model, embedding_dimensions,
                            status, chunks_count
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'INDEXED', ?)
                        """,
                documentId,
                fileName,
                contentType,
                contentHash,
                ai.embeddingProvider(),
                ai.embeddingModel(),
                ai.embeddingDimensions(),
                chunks.size()
        );

        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            jdbc.update("""
                            INSERT INTO document_chunks (
                                id, document_id, chunk_index, page_start, page_end,
                                heading, content, content_sha256, embedding
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                            """,
                    chunk.id(),
                    documentId,
                    chunk.index(),
                    chunk.pageStart(),
                    chunk.pageEnd(),
                    chunk.heading(),
                    chunk.content(),
                    chunk.contentHash(),
                    toVectorLiteral(embeddings.get(i))
            );
        }
        return findIndexedByContentHash(contentHash).orElseThrow();
    }

    @Override
    public List<RetrievedChunk> search(
            float[] queryEmbedding,
            UUID documentId,
            int topK,
            double minimumScore
    ) {
        var filter = documentId == null ? "" : " AND d.id = ? ";
        var sql = """
                WITH query_vector AS (SELECT ?::vector AS value)
                SELECT c.id AS chunk_id, d.id AS document_id, d.file_name,
                       c.page_start, c.page_end, c.heading, c.content, c.content_sha256,
                       1 - (c.embedding <=> q.value) AS score
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                CROSS JOIN query_vector q
                WHERE d.status = 'INDEXED'
                  AND d.embedding_provider = ?
                  AND d.embedding_model = ?
                  AND d.embedding_dimensions = ?
                """ + filter + """
                  AND 1 - (c.embedding <=> q.value) >= ?
                ORDER BY c.embedding <=> q.value
                LIMIT ?
                """;

        var ai = properties.ai();
        if (documentId == null) {
            return jdbc.query(
                    sql,
                    this::mapChunk,
                    toVectorLiteral(queryEmbedding),
                    ai.embeddingProvider(),
                    ai.embeddingModel(),
                    ai.embeddingDimensions(),
                    minimumScore,
                    topK
            );
        }
        return jdbc.query(
                sql,
                this::mapChunk,
                toVectorLiteral(queryEmbedding),
                ai.embeddingProvider(),
                ai.embeddingModel(),
                ai.embeddingDimensions(),
                documentId,
                minimumScore,
                topK
        );
    }

    private IndexedDocument mapDocument(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp uploadedAt = rs.getTimestamp("uploaded_at");
        return new IndexedDocument(
                rs.getObject("id", UUID.class),
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getString("content_sha256"),
                rs.getInt("chunks_count"),
                uploadedAt.toInstant()
        );
    }

    private RetrievedChunk mapChunk(ResultSet rs, int rowNumber) throws SQLException {
        return new RetrievedChunk(
                rs.getObject("chunk_id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("file_name"),
                (Integer) rs.getObject("page_start"),
                (Integer) rs.getObject("page_end"),
                rs.getString("heading"),
                rs.getString("content"),
                rs.getString("content_sha256"),
                rs.getDouble("score")
        );
    }

    private String toVectorLiteral(float[] vector) {
        var value = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(Float.toString(vector[i]));
        }
        return value.append(']').toString();
    }
}
