package io.github.kadaferhidup.minibank.common;

import org.springframework.http.HttpStatus;

/** Kode error yang stabil, supaya aplikasi mobile bisa menampilkan pesan yang tepat. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    WEAK_PIN(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_PIN(HttpStatus.UNPROCESSABLE_ENTITY),
    PIN_LOCKED(HttpStatus.LOCKED),
    PIN_NOT_SET(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY),
    SAME_ACCOUNT(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    DESTINATION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_STATE(HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
