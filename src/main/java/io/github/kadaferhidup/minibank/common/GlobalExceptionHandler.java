package io.github.kadaferhidup.minibank.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Semua error dikembalikan dalam format RFC 7807 (application/problem+json) dengan field "code". */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> framework(Exception ex) {
        ErrorResponse er = (ErrorResponse) ex;
        ErrorCode code = switch (er.getStatusCode().value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.VALIDATION_FAILED;
        };
        return ResponseEntity.status(er.getStatusCode()).headers(er.getHeaders())
                .body(problem(code, er.getBody().getDetail()));
    }

    /** Error tak terduga: dicatat lengkap di log, tapi detailnya tidak dibocorkan ke klien. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Error tak terduga", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Terjadi kesalahan pada sistem, silakan coba lagi");
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
