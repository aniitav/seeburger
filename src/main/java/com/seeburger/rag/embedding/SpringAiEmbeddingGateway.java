package com.seeburger.rag.embedding;

import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.error.DependencyUnavailableException;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiEmbeddingGateway implements EmbeddingGateway {
    private final EmbeddingModel embeddingModel;
    private final RagProperties properties;

    public SpringAiEmbeddingGateway(
            Map<String, EmbeddingModel> embeddingModels,
            RagProperties properties
    ) {
        this.embeddingModel = selectModel(embeddingModels, properties.ai().embeddingProvider());
        this.properties = properties;
    }

    @Override
    public List<float[]> embedDocuments(List<String> content) {
        try {
            var result = new ArrayList<float[]>(content.size());
            var batchSize = properties.ai().embeddingBatchSize();
            for (int from = 0; from < content.size(); from += batchSize) {
                var to = Math.min(from + batchSize, content.size());
                result.addAll(embed(content.subList(from, to), options(false)));
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw new DependencyUnavailableException("The embedding provider", exception);
        }
    }

    @Override
    public float[] embedQuery(String query) {
        try {
            return embed(List.of(query), options(true)).get(0);
        } catch (RuntimeException exception) {
            throw new DependencyUnavailableException("The embedding provider", exception);
        }
    }

    private List<float[]> embed(List<String> input, EmbeddingOptions options) {
        return embeddingModel.call(new EmbeddingRequest(input, options))
                .getResults()
                .stream()
                .sorted(Comparator.comparingInt(Embedding::getIndex))
                .map(Embedding::getOutput)
                .map(this::normalizeAndValidate)
                .toList();
    }

    private EmbeddingOptions options(boolean query) {
        var ai = properties.ai();
        return switch (ai.embeddingProvider().toLowerCase()) {
            case "google-genai" -> GoogleGenAiTextEmbeddingOptions.builder()
                    .model(ai.embeddingModel())
                    .dimensions(ai.embeddingDimensions())
                    .taskType(query ? TaskType.RETRIEVAL_QUERY : TaskType.RETRIEVAL_DOCUMENT)
                    .autoTruncate(false)
                    .build();
            case "openai" -> OpenAiEmbeddingOptions.builder()
                    .model(ai.embeddingModel())
                    .dimensions(ai.embeddingDimensions())
                    .build();
            default -> throw new IllegalStateException(
                    "Unsupported embedding provider: " + ai.embeddingProvider()
            );
        };
    }

    private float[] normalizeAndValidate(float[] vector) {
        if (vector.length != properties.ai().embeddingDimensions()) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: expected %d but received %d"
                            .formatted(properties.ai().embeddingDimensions(), vector.length)
            );
        }
        double magnitudeSquared = 0;
        for (var value : vector) {
            magnitudeSquared += value * value;
        }
        if (magnitudeSquared == 0) {
            throw new IllegalStateException("The embedding provider returned a zero vector");
        }
        var magnitude = Math.sqrt(magnitudeSquared);
        var normalized = vector.clone();
        for (int i = 0; i < normalized.length; i++) {
            normalized[i] = (float) (normalized[i] / magnitude);
        }
        return normalized;
    }

    private EmbeddingModel selectModel(Map<String, EmbeddingModel> models, String provider) {
        var beanName = switch (provider.toLowerCase()) {
            case "google-genai" -> "googleGenAiTextEmbedding";
            case "openai" -> "openAiEmbeddingModel";
            default -> throw new IllegalStateException("Unsupported embedding provider: " + provider);
        };
        var selected = models.get(beanName);
        if (selected == null) {
            throw new IllegalStateException(
                    "Embedding provider '%s' is configured but bean '%s' is unavailable. Available beans: %s"
                            .formatted(provider, beanName, models.keySet())
            );
        }
        return selected;
    }
}
