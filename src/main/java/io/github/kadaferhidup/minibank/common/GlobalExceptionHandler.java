package io.github.kadaferhidup.minibank.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Semua error dikembalikan dalam format RFC 7807 (application/problem+json) dengan field "code". */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex) {
        return problem(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, "Data yang dikirim tidak valid");
        pd.setProperty("fields", fields);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception ex) {
        return problem(ErrorCode.VALIDATION_FAILED, "Format permintaan tidak bisa dibaca");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException ex) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return problem(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Header Idempotency-Key wajib diisi untuk transaksi uang");
        }
        return problem(ErrorCode.VALIDATION_FAILED, "Header " + ex.getHeaderName() + " wajib diisi");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail concurrent(Exception ex) {
        log.warn("Bentrok update bersamaan: {}", ex.getMessage());
        return problem(ErrorCode.CONCURRENT_UPDATE, "Data sedang diproses permintaan lain, silakan coba lagi");
    }

    private ProblemDetail problem(ErrorCode code, String message) {
        HttpStatus status = code.status();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("https://minibank.dev/errors/" + code.name().toLowerCase().replace('_', '-')));
        pd.setProperty("code", code.name());
        return pd;
    }
}
