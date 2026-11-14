package com.arena.login.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@io.swagger.v3.oas.annotations.tags.Tag(name="Service metadata")
@RestController
public class HomeController {

  @Value("${APP_BASE_URL:http://localhost:4200}")
  private String appBaseUrl;

  @io.swagger.v3.oas.annotations.Operation(summary="Get service metadata", description="Service metadata. Login root is public; Core root requires JWT. Health endpoint is public. /api/me reports whether a Spring OAuth2 principal exists and does not establish a BFF session. /register redirects to the configured frontend registration page.")
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Service response",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.arena.login.documentation.ResponseDocumentation.ServiceStatus.class)))
  @GetMapping("/")
  public Map<String, String> home() {
    return Map.of("service", "arena-login", "status", "ready");
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Inspect the legacy Spring OAuth2 principal", description="Service metadata. Login root is public; Core root requires JWT. Health endpoint is public. /api/me reports whether a Spring OAuth2 principal exists and does not establish a BFF session. /register redirects to the configured frontend registration page.")
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Service response",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.arena.login.documentation.ResponseDocumentation.PrincipalState.class)))
  @GetMapping("/api/me")
  public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
    return Map.of("authenticated", user != null, "user", user == null ? Map.of() : user.getAttributes());
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Redirect to frontend registration", description="Service metadata. Login root is public; Core root requires JWT. Health endpoint is public. /api/me reports whether a Spring OAuth2 principal exists and does not establish a BFF session. /register redirects to the configured frontend registration page.")
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="302", description="Redirect to configured frontend registration",headers=@io.swagger.v3.oas.annotations.headers.Header(name="Location",description="Configured frontend registration URL",schema=@io.swagger.v3.oas.annotations.media.Schema(type="string",format="uri")))
 @GetMapping("/register")
  public ResponseEntity<Void> register() {
    // Registration is an ArenaOps UI flow. The browser owns PKCE/state for later sign-in.
    String location = UriComponentsBuilder.fromUriString(appBaseUrl).path("/register").build().toUriString();
    return ResponseEntity.status(302).header("Location", location).build();
  }
}
