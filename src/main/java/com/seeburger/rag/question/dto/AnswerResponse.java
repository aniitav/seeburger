package com.seeburger.rag.question.dto;

import java.util.List;

public record AnswerResponse(
        String question,
        String answer,
        boolean found,
        List<SourceResponse> sources
) {
    public AnswerResponse {
        sources = List.copyOf(sources);
    }
}
