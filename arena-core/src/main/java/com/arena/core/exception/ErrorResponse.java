package com.arena.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String timestamp, int status, ErrorCode errorCode, String reasonCode,
        String description, String path, String correlationId, Map<String, String> fieldErrors) {}
