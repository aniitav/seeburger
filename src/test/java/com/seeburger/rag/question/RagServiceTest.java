package com.seeburger.rag.question;

import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.embedding.EmbeddingGateway;
import com.seeburger.rag.llm.AnswerGenerator;
import com.seeburger.rag.vector.VectorRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @Test
    void allowsQuestionsPreviouslyClassifiedByAnEnglishWordList() {
        var embedding = mock(EmbeddingGateway.class);
        var repository = mock(VectorRepository.class);
        var contextBuilder = mock(ContextBuilder.class);
        var answerGenerator = mock(AnswerGenerator.class);
        var service = new RagService(
                embedding, repository, contextBuilder, answerGenerator, properties()
        );
        var vector = new float[1536];
        when(embedding.embedQuery("What?")).thenReturn(vector);
        when(repository.search(vector, null, 5, 0.65)).thenReturn(List.of());
        when(contextBuilder.build(List.of())).thenReturn(new BuiltContext("", List.of()));

        var response = service.ask("What?", null);

        assertThat(response.question()).isEqualTo("What?");
        assertThat(response.found()).isFalse();
    }

    @Test
    void doesNotCallTheLlmWhenRetrievalFindsNoEvidence() {
        var embedding = mock(EmbeddingGateway.class);
        var repository = mock(VectorRepository.class);
        var contextBuilder = mock(ContextBuilder.class);
        var answerGenerator = mock(AnswerGenerator.class);
        var service = new RagService(
                embedding, repository, contextBuilder, answerGenerator, properties()
        );
        var vector = new float[1536];
        when(embedding.embedQuery("What is the travel budget?")).thenReturn(vector);
        when(repository.search(vector, null, 5, 0.65)).thenReturn(List.of());
        when(contextBuilder.build(List.of())).thenReturn(new BuiltContext("", List.of()));

        var response = service.ask("What is the travel budget?", null);

        assertThat(response.found()).isFalse();
        assertThat(response.answer()).isEqualTo(RagService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        verify(answerGenerator, never()).answer(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private RagProperties properties() {
        return new RagProperties(
                new RagProperties.Upload(10_000, 100_000),
                new RagProperties.Chunk(800, 100, 40),
                new RagProperties.Retrieval(5, 0.65),
                new RagProperties.Context(4000),
                new RagProperties.Ai(
                        "openai", "text-embedding-3-small", 1536, 100,
                        "openai", "gpt-5.6-luna", 1000
                )
        );
    }
}
