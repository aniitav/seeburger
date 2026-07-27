package com.seeburger.rag.document;

import java.util.List;

public record ParsedDocument(List<ParsedPage> pages) {
    public ParsedDocument {
        pages = List.copyOf(pages);
    }
}
