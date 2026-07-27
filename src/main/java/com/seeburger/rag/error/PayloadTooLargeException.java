package com.seeburger.rag.error;

public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(long maxBytes) {
        super("The file exceeds the configured maximum of " + maxBytes + " bytes.");
    }

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
