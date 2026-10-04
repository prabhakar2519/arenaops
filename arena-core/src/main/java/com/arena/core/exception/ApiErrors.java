package com.arena.core.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiErrors {
    private final ObjectMapper objectMapper;

    public static ErrorResponse body(ErrorCode code, int status, HttpServletRequest request,
            Map<String, String> fields) {
        return new ErrorResponse(Instant.now().toString(), status, code,
                fields == null ? null : "INVALID_REQUEST_FIELDS", code.getDescription(),
                request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI) instanceof String original
                        ? original : request.getRequestURI(),
                CorrelationIdFilter.correlationId(request), fields);
    }

    public static ResponseEntity<ErrorResponse> response(ErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(code.getStatus()).header(CorrelationIdFilter.HEADER,
                CorrelationIdFilter.correlationId(request)).body(body(code, code.getStatus(), request, null));
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER, CorrelationIdFilter.correlationId(request));
        objectMapper.writeValue(response.getOutputStream(), body(code, code.getStatus(), request, null));
    }
}
