package com.seeburger.rag.error;

public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " is currently unavailable.", cause);
    }
}
