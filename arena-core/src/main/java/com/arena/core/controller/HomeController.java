package com.arena.core.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name="Service metadata")
@RestController
public class HomeController {

  @io.swagger.v3.oas.annotations.Operation(summary="Get service metadata", description="Service metadata. Login root is public; Core root requires JWT. Health endpoint is public. /api/me reports whether a Spring OAuth2 principal exists and does not establish a BFF session. /register redirects to the configured frontend registration page.")
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Service response",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.arena.core.documentation.ResponseDocumentation.ServiceStatus.class)))
  @GetMapping("/")
  public Map<String, String> home() {
    return Map.of("service", "arena-core", "status", "ready");
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Check Core liveness", description="Service metadata. Login root is public; Core root requires JWT. Health endpoint is public. /api/me reports whether a Spring OAuth2 principal exists and does not establish a BFF session. /register redirects to the configured frontend registration page.")
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Service response",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.arena.core.documentation.ResponseDocumentation.Health.class)))
  @GetMapping("/api/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
