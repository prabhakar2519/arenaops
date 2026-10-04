package com.arena.login.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class HomeController {

  @Value("${APP_BASE_URL:http://localhost:4200}")
  private String appBaseUrl;

  @GetMapping("/")
  public Map<String, String> home() {
    return Map.of("service", "arena-login", "status", "ready");
  }

  @GetMapping("/api/me")
  public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
    return Map.of("authenticated", user != null, "user", user == null ? Map.of() : user.getAttributes());
  }

  @GetMapping("/register")
  public ResponseEntity<Void> register() {
    // Registration is an ArenaOps UI flow. The browser owns PKCE/state for later sign-in.
    String location = UriComponentsBuilder.fromUriString(appBaseUrl).path("/register").build().toUriString();
    return ResponseEntity.status(302).header("Location", location).build();
  }
}
