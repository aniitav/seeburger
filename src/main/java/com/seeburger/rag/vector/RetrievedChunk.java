package com.seeburger.rag.vector;

import java.util.UUID;

public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String fileName,
        Integer pageStart,
        Integer pageEnd,
        String heading,
        String content,
        String contentHash,
        double score
) {}
