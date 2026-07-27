package com.seeburger.rag.chunking;

import com.seeburger.rag.document.ParsedDocument;

import java.util.List;

public interface ChunkingStrategy {
    List<DocumentChunk> chunk(ParsedDocument document);
}
