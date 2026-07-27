package com.seeburger.rag.question;

import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.embedding.EmbeddingGateway;
import com.seeburger.rag.llm.AnswerGenerator;
import com.seeburger.rag.vector.RetrievedChunk;
import com.seeburger.rag.vector.VectorRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
        when(repository.search("What?", vector, null, 5, 0.50)).thenReturn(List.of());
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
        when(repository.search("What is the travel budget?", vector, null, 5, 0.50))
                .thenReturn(List.of());
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

    @Test
    void returnsNotFoundWhenTheAnswerModelRejectsTheRetrievedContext() {
        var embedding = mock(EmbeddingGateway.class);
        var repository = mock(VectorRepository.class);
        var contextBuilder = mock(ContextBuilder.class);
        var answerGenerator = mock(AnswerGenerator.class);
        var service = new RagService(
                embedding, repository, contextBuilder, answerGenerator, properties()
        );
        var question = "How many employees are there?";
        var vector = new float[1536];
        var chunk = new RetrievedChunk(
                UUID.randomUUID(), UUID.randomUUID(), "handbook.txt",
                1, 1, "Annual Leave", "Employees receive annual leave.", "hash", 0.7
        );
        var context = new BuiltContext(
                "[Source 1]\nEmployees receive annual leave.",
                List.of(new ContextSource(1, chunk))
        );
        when(embedding.embedQuery(question)).thenReturn(vector);
        when(repository.search(question, vector, null, 5, 0.50)).thenReturn(List.of(chunk));
        when(contextBuilder.build(List.of(chunk))).thenReturn(context);
        when(answerGenerator.answer(question, context.promptContext()))
                .thenReturn(RagService.NO_EVIDENCE);

        var response = service.ask(question, null);

        assertThat(response.found()).isFalse();
        assertThat(response.answer()).isEqualTo(RagService.NO_EVIDENCE);
        assertThat(response.sources()).isEmpty();
    }

    private RagProperties properties() {
        return new RagProperties(
                new RagProperties.Upload(10_000, 100_000),
                new RagProperties.Chunk(800, 100, 40),
                new RagProperties.Retrieval(5, 20, 0.50, 0.7, 0.3),
                new RagProperties.Context(4000),
                new RagProperties.Ai(
                        "openai", "text-embedding-3-small", 1536, 100,
                        "openai", "gpt-5.6-luna", 1000
                )
        );
    }
}
