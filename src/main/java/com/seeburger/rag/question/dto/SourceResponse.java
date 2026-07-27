package com.seeburger.rag.question.dto;

import java.util.UUID;

public record SourceResponse(
        int sourceNumber,
        UUID documentId,
        String fileName,
        Integer pageNumber,
        Integer pageEnd,
        String heading,
        UUID chunkId,
        double score
) {}
