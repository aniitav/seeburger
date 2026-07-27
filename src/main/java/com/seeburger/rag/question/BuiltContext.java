package com.seeburger.rag.question;

import java.util.List;

public record BuiltContext(String promptContext, List<ContextSource> sources) {
    public BuiltContext {
        sources = List.copyOf(sources);
    }
}
