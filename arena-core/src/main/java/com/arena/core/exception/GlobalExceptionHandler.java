package com.arena.core.exception;

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
    @ExceptionHandler(com.arena.core.facility.FacilityFailure.class)
    public ResponseEntity<Map<String,Object>> facility(com.arena.core.facility.FacilityFailure ex,HttpServletRequest request){
        var base=ApiErrors.body(ex.getErrorCode(),ex.getErrorCode().getStatus(),request,ex.getFields());
        Map<String,Object> body=new LinkedHashMap<>();body.put("timestamp",base.timestamp());body.put("status",base.status());body.put("errorCode",base.errorCode());body.put("reasonCode",ex.getReasonCode());body.put("description",base.description());body.put("path",base.path());body.put("correlationId",base.correlationId());body.put("fieldErrors",ex.getFields());body.put("conflicts",ex.getConflicts());
        return ResponseEntity.status(base.status()).header(CorrelationIdFilter.HEADER,base.correlationId()).body(body);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> uploadTooLarge(Exception ex,HttpServletRequest request){return ApiErrors.response(ErrorCode.LOGO_TOO_LARGE,request);}

    @ExceptionHandler(com.arena.core.training.ActivitySelectionException.class)
    public ResponseEntity<ErrorResponse> selection(com.arena.core.training.ActivitySelectionException ex,HttpServletRequest request){return ResponseEntity.badRequest().body(ApiErrors.body(ErrorCode.VALIDATION_FAILED,400,request,ex.fields()));}

    @ExceptionHandler(ArenaOpsException.class)
    public ResponseEntity<ErrorResponse> business(ArenaOpsException ex, HttpServletRequest request) {
        log.info("Request rejected correlationId={} errorCode={}", CorrelationIdFilter.correlationId(request), ex.getErrorCode());
        if(ex.getErrorCode()==ErrorCode.DUPLICATE_USERNAME||ex.getErrorCode()==ErrorCode.DUPLICATE_EMAIL){String field=ex.getErrorCode()==ErrorCode.DUPLICATE_USERNAME?"username":"email";return ResponseEntity.status(409).body(ApiErrors.body(ex.getErrorCode(),409,request,Map.of(field,ex.getErrorCode().getDescription())));}
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

    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class, org.springframework.web.multipart.MultipartException.class, HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ex, HttpServletRequest request) {
        if("/api/public/club/reservations".equals(request.getRequestURI()))return ApiErrors.response(ErrorCode.PUBLIC_BOOKING_INVALID_DETAILS,request);
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

    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> storageUnavailable(Exception ex,HttpServletRequest request){return ApiErrors.response(ErrorCode.SERVICE_UNAVAILABLE,request);}

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        if(request.getRequestURI().startsWith("/api/v1/academies/"))return ApiErrors.response(ErrorCode.FACILITY_CONCURRENCY_FAILURE,request);
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "ux_customer_owner_email_normalized".equals(violation.getConstraintName())) {
                return ApiErrors.response(ErrorCode.CUSTOMER_ALREADY_EXISTS, request);
            }
        }
        return ApiErrors.response(ErrorCode.CONFLICT, request);
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> concurrentUpdate(Exception ex, HttpServletRequest request) {
        if(request.getRequestURI().startsWith("/api/v1/academies/"))return ApiErrors.response(ErrorCode.FACILITY_CONCURRENCY_FAILURE,request);
        return ApiErrors.response(ErrorCode.CONFLICT, request);
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
