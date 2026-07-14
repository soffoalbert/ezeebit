package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.domain.exception.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain and validation failures into RFC-7807 problem responses, mapping
 * each stable domain error code to an HTTP status.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WalletException.class)
    ProblemDetail handleWallet(WalletException e) {
        HttpStatus status = statusFor(e.code());
        if (status.is5xxServerError()) {
            log.error("wallet error [{}]: {}", e.code(), e.getMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("code", e.code());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst().orElse("validation failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", "VALIDATION_FAILED");
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "missing required header: " + e.getHeaderName());
        problem.setProperty("code", "MISSING_HEADER");
        return problem;
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "INSUFFICIENT_FUNDS", "CURRENCY_MISMATCH", "IDEMPOTENCY_CONFLICT",
                 "WITHDRAWAL_LIMIT_EXCEEDED", "INVALID_DESTINATION" ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case "ACCOUNT_NOT_FOUND", "QUOTE_NOT_FOUND", "WITHDRAWAL_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "QUOTE_EXPIRED", "QUOTE_ALREADY_USED", "ILLEGAL_WITHDRAWAL_STATE", "CONCURRENT_REQUEST" ->
                    HttpStatus.CONFLICT;
            case "INVALID_RATE" -> HttpStatus.BAD_GATEWAY;
            case "RATE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
