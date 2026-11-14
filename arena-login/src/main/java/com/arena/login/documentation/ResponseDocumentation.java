package com.arena.login.documentation;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
public final class ResponseDocumentation {
 public record Readiness(@Schema(example="UP") String status){}
 public record ServiceStatus(@Schema(example="arena-login") String service,@Schema(example="ready") String status){}
 public record PrincipalState(boolean authenticated,Map<String,Object> user){}
}
