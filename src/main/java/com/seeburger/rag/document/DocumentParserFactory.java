package com.seeburger.rag.document;

import com.seeburger.rag.error.UnsupportedDocumentTypeException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserFactory {
    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public DocumentParser get(String contentType, String fileName) {
        return parsers.stream()
                .filter(parser -> parser.supports(contentType, fileName))
                .findFirst()
                .orElseThrow(UnsupportedDocumentTypeException::new);
    }
}
