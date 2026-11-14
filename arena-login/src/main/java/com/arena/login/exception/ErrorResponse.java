package com.arena.login.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String timestamp, int status, ErrorCode errorCode, String reasonCode,
        String description, String path, String correlationId, Map<String, String> fieldErrors, java.util.List<Map<String,Object>> conflicts) {
 public ErrorResponse(String timestamp,int status,ErrorCode errorCode,String reasonCode,String description,String path,String correlationId,Map<String,String> fields){this(timestamp,status,errorCode,reasonCode,description,path,correlationId,fields,null);}
}
