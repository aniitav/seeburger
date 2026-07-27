package com.seeburger.rag.vector;

import com.seeburger.rag.chunking.DocumentChunk;
import com.seeburger.rag.config.RagProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private final NamedParameterJdbcTemplate namedJdbc;
    private final RagProperties properties;

    public PgVectorRepository(JdbcTemplate jdbc, RagProperties properties) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
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
            String query,
            float[] queryEmbedding,
            UUID documentId,
            int topK,
            double minimumScore
    ) {
        var sql = """
                WITH lexeme_data AS (
                    SELECT CAST(:embedding AS vector) AS embedding,
                           tsvector_to_array(to_tsvector('english', :query)) AS lexemes
                ),
                query_data AS (
                    SELECT embedding,
                           lexemes,
                           CASE
                               WHEN cardinality(lexemes) = 0 THEN NULL
                               ELSE to_tsquery('english', array_to_string(lexemes, ' | '))
                           END AS text_query
                    FROM lexeme_data
                ),
                vector_ranked AS (
                    SELECT c.id,
                           1 - (c.embedding <=> q.embedding) AS vector_score,
                           ROW_NUMBER() OVER (
                               ORDER BY c.embedding <=> q.embedding, c.id
                           ) AS vector_rank
                    FROM document_chunks c
                    JOIN documents d ON d.id = c.document_id
                    CROSS JOIN query_data q
                    WHERE d.status = 'INDEXED'
                      AND d.embedding_provider = :embeddingProvider
                      AND d.embedding_model = :embeddingModel
                      AND d.embedding_dimensions = :embeddingDimensions
                      AND (
                          CAST(:documentId AS uuid) IS NULL
                          OR d.id = CAST(:documentId AS uuid)
                      )
                ),
                vector_candidates AS (
                    SELECT id, vector_score, vector_rank
                    FROM vector_ranked
                    WHERE vector_rank <= :candidateK
                ),
                text_ranked AS (
                    SELECT c.id,
                           CASE
                               WHEN cardinality(q.lexemes) = 0 THEN 0.0
                               ELSE (
                                   SELECT COUNT(*)::double precision
                                   FROM unnest(q.lexemes) AS terms(lexeme)
                                   WHERE terms.lexeme = ANY(tsvector_to_array(c.search_vector))
                               ) / cardinality(q.lexemes)
                           END AS text_score,
                           ROW_NUMBER() OVER (
                               ORDER BY ts_rank_cd(c.search_vector, q.text_query) DESC, c.id
                           ) AS text_rank
                    FROM document_chunks c
                    JOIN documents d ON d.id = c.document_id
                    CROSS JOIN query_data q
                    WHERE d.status = 'INDEXED'
                      AND d.embedding_provider = :embeddingProvider
                      AND d.embedding_model = :embeddingModel
                      AND d.embedding_dimensions = :embeddingDimensions
                      AND (
                          CAST(:documentId AS uuid) IS NULL
                          OR d.id = CAST(:documentId AS uuid)
                      )
                      AND q.text_query IS NOT NULL
                      AND c.search_vector @@ q.text_query
                ),
                text_candidates AS (
                    SELECT id, text_score, text_rank
                    FROM text_ranked
                    WHERE text_rank <= :candidateK
                ),
                candidate_ranks AS (
                    SELECT id,
                           MAX(vector_score) AS vector_score,
                           MIN(vector_rank) AS vector_rank,
                           MAX(text_score) AS text_score,
                           MIN(text_rank) AS text_rank
                    FROM (
                        SELECT id, vector_score, vector_rank,
                               NULL::double precision AS text_score,
                               NULL::bigint AS text_rank
                        FROM vector_candidates
                        UNION ALL
                        SELECT id, NULL::double precision, NULL::bigint,
                               text_score, text_rank
                        FROM text_candidates
                    ) candidates
                    GROUP BY id
                ),
                eligible AS (
                    SELECT *
                    FROM candidate_ranks
                    WHERE COALESCE(vector_score, -1.0) >= :minimumScore
                       OR COALESCE(text_score, 0.0) >= :minimumScore
                ),
                scored AS (
                    SELECT c.id AS chunk_id,
                           d.id AS document_id,
                           d.file_name,
                           c.page_start,
                           c.page_end,
                           c.heading,
                           c.content,
                           c.content_sha256,
                           (
                               :vectorWeight * CASE
                                   WHEN candidates.vector_rank IS NULL THEN 0.0
                                   ELSE 61.0 / (60.0 + candidates.vector_rank)
                               END
                           ) + (
                               :textWeight * CASE
                                   WHEN candidates.text_rank IS NULL THEN 0.0
                                   ELSE 61.0 / (60.0 + candidates.text_rank)
                               END
                           ) AS score
                    FROM eligible candidates
                    JOIN document_chunks c ON c.id = candidates.id
                    JOIN documents d ON d.id = c.document_id
                )
                SELECT chunk_id, document_id, file_name,
                       page_start, page_end, heading, content, content_sha256, score
                FROM scored
                ORDER BY score DESC, chunk_id
                LIMIT :topK
                """;

        var ai = properties.ai();
        var retrieval = properties.retrieval();
        var parameters = new MapSqlParameterSource()
                .addValue("query", query)
                .addValue("embedding", toVectorLiteral(queryEmbedding))
                .addValue("embeddingProvider", ai.embeddingProvider())
                .addValue("embeddingModel", ai.embeddingModel())
                .addValue("embeddingDimensions", ai.embeddingDimensions())
                .addValue("documentId", documentId)
                .addValue("candidateK", retrieval.candidateK())
                .addValue("vectorWeight", retrieval.vectorWeight())
                .addValue("textWeight", retrieval.textWeight())
                .addValue("minimumScore", minimumScore)
                .addValue("topK", topK);
        return namedJdbc.query(sql, parameters, this::mapChunk);
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
