package com.example.demo.common.security;

import java.time.Instant;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Produces a stable JSON error body across the REST services, and maps every <em>foreseeable</em>
 * failure to a specific status so that <b>500 only ever means an unforeseen bug</b>:
 * <ul>
 *   <li>business and validation failures: their 4xx ({@link ResponseStatusException}, 400 for bad input);</li>
 *   <li>a downstream service or the database unreachable or timing out (an outage) → <b>503</b>;</li>
 *   <li>a downstream service answering with an error or an unreadable body → <b>502</b>;</li>
 *   <li>a unique-constraint or concurrent-modification conflict → <b>409</b>.</li>
 * </ul>
 * Clients should still translate failures close to the call with a specific message; these handlers
 * are the safety net. Code must never produce a 500 on purpose (enforced by ArchUnit and PMD).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorHandler {

    private static final Log LOG = LogFactory.getLog(ApiErrorHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> responseStatusException(ResponseStatusException exception,
                                                             HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? status.getReasonPhrase()
                : exception.getReason();
        return error(status, message, request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request content.", request);
    }

    /** A downstream call could not be completed: connection refused, DNS, I/O error or timeout. */
    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ApiErrorResponse> downstreamUnreachable(ResourceAccessException exception,
                                                           HttpServletRequest request) {
        LOG.warn("Downstream service unreachable on " + request.getRequestURI() + ": " + exception.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "A downstream service is unavailable.", request);
    }

    /** A downstream call completed but failed: an error status or a body that could not be read. */
    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiErrorResponse> downstreamFailed(RestClientException exception, HttpServletRequest request) {
        LOG.warn("Downstream service failed on " + request.getRequestURI() + ": " + exception.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "A downstream service returned an invalid response.", request);
    }

    /** The database is unreachable, or a transient lock/connection failure prevents completing the request. */
    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        CannotCreateTransactionException.class,
        TransientDataAccessException.class
    })
    ResponseEntity<ApiErrorResponse> databaseUnavailable(Exception exception, HttpServletRequest request) {
        LOG.warn("Database unavailable on " + request.getRequestURI() + ": " + exception.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "The database is unavailable.", request);
    }

    /** A write raced another write: a unique constraint or an optimistic/pessimistic lock was violated. */
    @ExceptionHandler({
        DataIntegrityViolationException.class,
        OptimisticLockingFailureException.class,
        PessimisticLockingFailureException.class
    })
    ResponseEntity<ApiErrorResponse> conflict(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource.", request);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(Instant.now().toString(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI()));
    }

    record ApiErrorResponse(String timestamp, int status, String error, String message, String path) {
    }
}
