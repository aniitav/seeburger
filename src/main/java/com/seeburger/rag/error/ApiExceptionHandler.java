package com.seeburger.rag.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail malformedRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ProblemDetail unsupported(UnsupportedDocumentTypeException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported document type", exception.getMessage(), request);
    }

    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    ProblemDetail tooLarge(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", exception.getMessage(), request);
    }

    @ExceptionHandler(UnprocessableDocumentException.class)
    ProblemDetail unprocessable(UnprocessableDocumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Document cannot be processed", exception.getMessage(), request);
    }

    @ExceptionHandler({DependencyUnavailableException.class, DataAccessException.class})
    ProblemDetail unavailable(RuntimeException exception, HttpServletRequest request) {
        log.warn("A required dependency is unavailable", exception);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Dependency unavailable",
                "A required AI or database dependency is currently unavailable.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected request failure", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The request could not be completed.",
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://seeburger.example/problems/" + slug(title)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", MDC.get(TraceIdFilter.TRACE_ID));
        return problem;
    }

    private String slug(String title) {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
