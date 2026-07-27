package com.seeburger.rag.question;

import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.embedding.EmbeddingGateway;
import com.seeburger.rag.error.BadRequestException;
import com.seeburger.rag.llm.AnswerGenerator;
import com.seeburger.rag.question.dto.AnswerResponse;
import com.seeburger.rag.question.dto.SourceResponse;
import com.seeburger.rag.vector.VectorRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class RagService {
    public static final String NO_EVIDENCE =
            "I could not find enough information in the uploaded documents.";

    private final EmbeddingGateway embeddingGateway;
    private final VectorRepository vectorRepository;
    private final ContextBuilder contextBuilder;
    private final AnswerGenerator answerGenerator;
    private final RagProperties properties;

    public RagService(
            EmbeddingGateway embeddingGateway,
            VectorRepository vectorRepository,
            ContextBuilder contextBuilder,
            AnswerGenerator answerGenerator,
            RagProperties properties
    ) {
        this.embeddingGateway = embeddingGateway;
        this.vectorRepository = vectorRepository;
        this.contextBuilder = contextBuilder;
        this.answerGenerator = answerGenerator;
        this.properties = properties;
    }

    public AnswerResponse ask(String rawQuestion, UUID documentId) {
        var question = validateQuestion(rawQuestion);
        var queryEmbedding = embeddingGateway.embedQuery(question);
        var candidates = vectorRepository.search(
                question,
                queryEmbedding,
                documentId,
                properties.retrieval().topK(),
                properties.retrieval().minimumScore()
        );
        var context = contextBuilder.build(candidates);
        if (context.sources().isEmpty()) {
            return new AnswerResponse(question, NO_EVIDENCE, false, java.util.List.of());
        }

        var answer = answerGenerator.answer(question, context.promptContext());
        if (NO_EVIDENCE.equals(answer)) {
            return new AnswerResponse(question, NO_EVIDENCE, false, java.util.List.of());
        }
        var sources = context.sources().stream()
                .map(source -> new SourceResponse(
                        source.number(),
                        source.chunk().documentId(),
                        source.chunk().fileName(),
                        source.chunk().pageStart(),
                        source.chunk().pageEnd(),
                        source.chunk().heading(),
                        source.chunk().chunkId(),
                        source.chunk().score()
                ))
                .toList();
        return new AnswerResponse(question, answer, true, sources);
    }

    private String validateQuestion(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            throw new BadRequestException("The q query parameter must not be blank.");
        }
        var question = rawQuestion.trim().replaceAll("\\s+", " ");
        if (question.length() > 2_000) {
            throw new BadRequestException("The question must not exceed 2000 characters.");
        }
        var normalized = question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        if (normalized.length() < 3) {
            throw new BadRequestException("The question must contain at least 3 letters or digits.");
        }
        return question;
    }
}
