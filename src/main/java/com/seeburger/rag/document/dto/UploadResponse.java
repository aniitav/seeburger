package com.seeburger.rag.document.dto;

import java.util.UUID;

public record UploadResponse(
        UUID documentId,
        String fileName,
        String contentType,
        int chunksCreated,
        String status
) {}
