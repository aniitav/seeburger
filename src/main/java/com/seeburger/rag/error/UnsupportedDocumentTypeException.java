package com.seeburger.rag.error;

public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException() {
        super("Only a raw text/plain body or an application/pdf file part is supported.");
    }
}
