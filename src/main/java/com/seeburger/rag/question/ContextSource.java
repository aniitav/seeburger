package com.seeburger.rag.question;

import com.seeburger.rag.vector.RetrievedChunk;

public record ContextSource(int number, RetrievedChunk chunk) {}
