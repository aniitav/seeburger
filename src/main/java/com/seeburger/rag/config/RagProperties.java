package com.seeburger.rag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @NotNull @Valid Upload upload,
        @NotNull @Valid Chunk chunk,
        @NotNull @Valid Retrieval retrieval,
        @NotNull @Valid Context context,
        @NotNull @Valid Ai ai
) {
    public record Upload(
            @Min(1) @Max(104_857_600) long maxBytes,
            @Min(100) @Max(2_000_000) int maxDocumentTokens
    ) {}

    public record Chunk(
            @Min(100) @Max(2_000) int maxTokens,
            @Min(0) @Max(600) int overlapTokens,
            @Min(1) @Max(500) int minTokens
    ) {
        public Chunk {
            if (overlapTokens >= maxTokens) {
                throw new IllegalArgumentException("rag.chunk.overlap-tokens must be smaller than max-tokens");
            }
            if (minTokens >= maxTokens) {
                throw new IllegalArgumentException("rag.chunk.min-tokens must be smaller than max-tokens");
            }
        }
    }

    public record Retrieval(
            @Min(1) @Max(20) int topK,
            @Min(1) @Max(100) int candidateK,
            @DecimalMin("0.0") @DecimalMax("1.0") double minimumScore,
            @DecimalMin("0.0") @DecimalMax("1.0") double vectorWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") double textWeight
    ) {
        public Retrieval {
            if (candidateK < topK) {
                throw new IllegalArgumentException("rag.retrieval.candidate-k must be at least top-k");
            }
            if (Math.abs(vectorWeight + textWeight - 1.0) > 0.000_001) {
                throw new IllegalArgumentException(
                        "rag.retrieval.vector-weight and text-weight must add up to 1.0"
                );
            }
        }
    }

    public record Context(@Min(100) @Max(20_000) int maxTokens) {}

    public record Ai(
            @NotBlank String embeddingProvider,
            @NotBlank String embeddingModel,
            @Min(1) int embeddingDimensions,
            @Min(1) @Max(2_048) int embeddingBatchSize,
            @NotBlank String chatProvider,
            @NotBlank String chatModel,
            @Min(1) @Max(16_384) int maxAnswerTokens
    ) {
        public Ai {
            if (embeddingDimensions != 1536) {
                throw new IllegalArgumentException(
                        "This schema is intentionally fixed at 1536 embedding dimensions; changing it requires a migration."
                );
            }
        }

        public String embeddingFingerprint() {
            return embeddingProvider + ":" + embeddingModel + ":" + embeddingDimensions;
        }
    }
}
