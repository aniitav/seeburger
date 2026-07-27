package com.seeburger.rag.document;

import com.seeburger.rag.document.dto.UploadResponse;

public record UploadResult(UploadResponse response, boolean created) {}
