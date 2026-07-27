package com.seeburger.rag.vector;

import com.seeburger.rag.chunking.DocumentChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VectorRepository {
    Optional<IndexedDocument> findIndexedByContentHash(String contentHash);

    IndexedDocument store(
            UUID documentId,
            String fileName,
            String contentType,
            String contentHash,
            List<DocumentChunk> chunks,
            List<float[]> embeddings
    );

    List<RetrievedChunk> search(
            float[] queryEmbedding,
            UUID documentId,
            int topK,
            double minimumScore
    );
}
