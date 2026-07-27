package com.seeburger.rag.document;

public interface DocumentParser {
    boolean supports(String contentType, String fileName);
    ParsedDocument parse(byte[] content);
}
