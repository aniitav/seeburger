package com.seeburger.rag.error;

public class UnprocessableDocumentException extends RuntimeException {
    public UnprocessableDocumentException(String message) {
        super(message);
    }

    public UnprocessableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
