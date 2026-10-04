package com.arena.login.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(ArenaOpsException.class)
    public ResponseEntity<ErrorResponse> business(ArenaOpsException ex, HttpServletRequest request) {
        log.info("Request rejected correlationId={} errorCode={}", CorrelationIdFilter.correlationId(request), ex.getErrorCode());
        return ApiErrors.response(ex.getErrorCode(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiErrors.body(ErrorCode.VALIDATION_FAILED, 400, request, fields));
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> parameterValidation(Exception ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (ex instanceof ConstraintViolationException validation) {
            validation.getConstraintViolations().forEach(error ->
                    fields.putIfAbsent(error.getPropertyPath().toString(), error.getMessage()));
        } else if (ex instanceof HandlerMethodValidationException validation) {
            if (validation.isForReturnValue()) return ApiErrors.response(ErrorCode.INTERNAL_SERVER_ERROR, request);
            validation.getAllValidationResults().forEach(result -> {
                String name = result.getMethodParameter().getParameterName();
                fields.putIfAbsent(name == null ? "parameter" : name, "Invalid value");
            });
        }
        return ResponseEntity.badRequest().body(ApiErrors.body(ErrorCode.VALIDATION_FAILED, 400, request, fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.INVALID_REQUEST, request);
    }

    @ExceptionHandler({NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaType(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> notAcceptable(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.NOT_ACCEPTABLE, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authentication(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.AUTHENTICATION_REQUIRED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(Exception ex, HttpServletRequest request) {
        return ApiErrors.response(ErrorCode.ACCESS_DENIED, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        return ResponseEntity.status(status).body(ApiErrors.body(ErrorCode.forStatus(status), status, request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        // Messages/bodies can contain credentials. Log frames rather than those values.
        log.error("Unexpected request failure correlationId={} type={} frames={}",
                CorrelationIdFilter.correlationId(request), ex.getClass().getSimpleName(),
                java.util.Arrays.stream(ex.getStackTrace()).limit(25).toList());
        return ApiErrors.response(ErrorCode.INTERNAL_SERVER_ERROR, request);
    }
}
