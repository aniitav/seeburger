package com.seeburger.rag.chunking;

import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.document.ParsedDocument;
import com.seeburger.rag.document.ParsedPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingAwareChunkerTest {

    @Test
    void preservesHeadingAndPageMetadataWhileBoundingChunks() {
        var properties = properties(30, 5, 1, 100);
        var chunker = new HeadingAwareChunker(new TokenEstimator(), properties);
        var document = new ParsedDocument(List.of(
                new ParsedPage(1, "# Leave Policy\n\nEmployees receive twenty days of annual leave. "
                        + "Leave requests must be approved by a manager."),
                new ParsedPage(2, "Carry-over is limited to five days. "
                        + "Unused leave above that limit expires.")
        ));

        var chunks = chunker.chunk(document);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(new TokenEstimator().estimate(chunk.content())).isLessThanOrEqualTo(30);
            assertThat(chunk.heading()).isEqualTo("Leave Policy");
            assertThat(chunk.pageStart()).isNotNull();
            assertThat(chunk.pageEnd()).isNotNull();
        });
    }

    @Test
    void splitsAParagraphThatExceedsTheLimit() {
        var properties = properties(10, 2, 1, 100);
        var chunker = new HeadingAwareChunker(new TokenEstimator(), properties);
        var longText = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu "
                + "nu xi omicron pi rho sigma tau upsilon phi chi psi omega";

        var chunks = chunker.chunk(new ParsedDocument(List.of(new ParsedPage(1, longText))));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(new TokenEstimator().estimate(chunk.content())).isLessThanOrEqualTo(10));
    }

    @Test
    void startsANewChunkWhenTheHeadingChangesWithoutCrossTopicOverlap() {
        var chunker = new HeadingAwareChunker(
                new TokenEstimator(),
                properties(800, 100, 40, 4000)
        );
        var document = new ParsedDocument(List.of(new ParsedPage(1, """
                # Annual Leave

                Employees receive twenty days of annual leave.

                # Remote Work

                Employees may work remotely three days per week.
                """)));

        var chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).heading()).isEqualTo("Annual Leave");
        assertThat(chunks.get(0).content()).contains("twenty days").doesNotContain("remotely");
        assertThat(chunks.get(1).heading()).isEqualTo("Remote Work");
        assertThat(chunks.get(1).content()).contains("remotely").doesNotContain("annual leave");
    }

    private RagProperties properties(int maxTokens, int overlap, int minTokens, int contextTokens) {
        return new RagProperties(
                new RagProperties.Upload(10_000, 100_000),
                new RagProperties.Chunk(maxTokens, overlap, minTokens),
                new RagProperties.Retrieval(5, 0.65),
                new RagProperties.Context(contextTokens),
                new RagProperties.Ai(
                        "openai", "text-embedding-3-small", 1536, 100,
                        "openai", "gpt-5.6-luna", 1000
                )
        );
    }
}
