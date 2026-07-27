package com.seeburger.rag.question;

import com.seeburger.rag.chunking.TokenEstimator;
import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.vector.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {

    @Test
    void deduplicatesChunksAndNumbersIncludedSources() {
        var builder = new ContextBuilder(new TokenEstimator(), properties(200));
        var first = chunk("hash-a", "Annual leave is twenty days.", 0.91);
        var duplicate = chunk("hash-a", "Annual leave is twenty days.", 0.90);
        var second = chunk("hash-b", "Carry-over is five days.", 0.85);

        var context = builder.build(List.of(first, duplicate, second));

        assertThat(context.sources()).hasSize(2);
        assertThat(context.sources()).extracting(ContextSource::number).containsExactly(1, 2);
        assertThat(context.promptContext()).contains("<source number=\"1\">", "<source number=\"2\">");
    }

    @Test
    void respectsTheGlobalContextBudget() {
        var builder = new ContextBuilder(new TokenEstimator(), properties(40));
        var context = builder.build(List.of(
                chunk("a", "A".repeat(80), 0.9),
                chunk("b", "B".repeat(80), 0.8)
        ));

        assertThat(context.sources()).hasSizeLessThanOrEqualTo(1);
    }

    private RetrievedChunk chunk(String hash, String content, double score) {
        return new RetrievedChunk(
                UUID.randomUUID(), UUID.randomUUID(), "handbook.pdf",
                1, 1, "Leave", content, hash, score
        );
    }

    private RagProperties properties(int contextTokens) {
        return new RagProperties(
                new RagProperties.Upload(10_000, 100_000),
                new RagProperties.Chunk(800, 100, 40),
                new RagProperties.Retrieval(5, 0.65),
                new RagProperties.Context(contextTokens),
                new RagProperties.Ai(
                        "openai", "text-embedding-3-small", 1536, 100,
                        "openai", "gpt-5.6-luna", 1000
                )
        );
    }
}
