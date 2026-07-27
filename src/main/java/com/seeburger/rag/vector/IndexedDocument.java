package com.seeburger.rag.vector;

import java.time.Instant;
import java.util.UUID;

public record IndexedDocument(
        UUID id,
        String fileName,
        String contentType,
        String contentHash,
        int chunksCount,
        Instant uploadedAt
) {}
