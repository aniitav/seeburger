package com.seeburger.rag.embedding;

import java.util.List;

public interface EmbeddingGateway {
    List<float[]> embedDocuments(List<String> content);
    float[] embedQuery(String query);
}
