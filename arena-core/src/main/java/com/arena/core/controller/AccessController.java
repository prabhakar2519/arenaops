package com.arena.core.controller;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.repository.AppUserRepository;
import com.arena.core.service.AdminAuthorizationService;
import com.arena.core.service.CustomerAccessService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessController {

  private final AppUserRepository appUserRepository;
  private final AdminAuthorizationService adminAuthorizationService;
  private final CustomerAccessService customerAccessService;

  @GetMapping("/check")
  public ResponseEntity<Map<String, Boolean>> check(@AuthenticationPrincipal Jwt jwt) {
    if (adminAuthorizationService.isAdmin(jwt)) {
      return ResponseEntity.ok(Map.of("allowed", true));
    }

    String username = adminAuthorizationService.resolveUsername(jwt);
    AppUserEntity user = appUserRepository.findByUsername(username)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.FORBIDDEN, "User is not registered in ArenaOps"));
    customerAccessService.assertCustomerCanUseApp(user);

    return ResponseEntity.ok(Map.of("allowed", true));
  }
}
