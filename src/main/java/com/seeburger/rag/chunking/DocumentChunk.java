package com.seeburger.rag.chunking;

import java.util.UUID;

public record DocumentChunk(
        UUID id,
        int index,
        Integer pageStart,
        Integer pageEnd,
        String heading,
        String content,
        String contentHash
) {}
