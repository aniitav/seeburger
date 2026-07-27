package com.seeburger.rag.question;

import com.seeburger.rag.chunking.TokenEstimator;
import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.vector.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class ContextBuilder {
    private final TokenEstimator tokenEstimator;
    private final RagProperties properties;

    public ContextBuilder(TokenEstimator tokenEstimator, RagProperties properties) {
        this.tokenEstimator = tokenEstimator;
        this.properties = properties;
    }

    public BuiltContext build(List<RetrievedChunk> candidates) {
        var included = new ArrayList<ContextSource>();
        var seenHashes = new HashSet<String>();
        var context = new StringBuilder();
        var usedTokens = 0;

        for (var candidate : candidates) {
            if (!seenHashes.add(candidate.contentHash())) {
                continue;
            }
            var number = included.size() + 1;
            var block = format(number, candidate);
            var blockTokens = tokenEstimator.estimate(block);
            if (usedTokens + blockTokens > properties.context().maxTokens()) {
                continue;
            }
            context.append(block);
            included.add(new ContextSource(number, candidate));
            usedTokens += blockTokens;
        }
        return new BuiltContext(context.toString(), included);
    }

    private String format(int number, RetrievedChunk chunk) {
        var page = chunk.pageStart() == null
                ? "unknown"
                : chunk.pageStart().equals(chunk.pageEnd())
                    ? chunk.pageStart().toString()
                    : chunk.pageStart() + "-" + chunk.pageEnd();
        return """
                <source number="%d">
                file: %s
                page: %s
                heading: %s
                content:
                %s
                </source>

                """.formatted(
                number,
                chunk.fileName(),
                page,
                chunk.heading() == null ? "none" : chunk.heading(),
                chunk.content()
        );
    }
}
