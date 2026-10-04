package com.arena.core.controller;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.repository.AppUserRepository;
import com.arena.core.service.AdminAuthorizationService;
import com.arena.core.service.CustomerAccessService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED));
    try {
      customerAccessService.assertCustomerCanUseApp(user);
    } catch (ArenaOpsException ex) {
      // Expired owners may sign in to purchase; operational APIs still enforce entitlement in the filter.
      if ("OWNER".equalsIgnoreCase(user.getRole()) && Boolean.TRUE.equals(user.getIsActive())
          && (ex.getErrorCode() == ErrorCode.PAYMENT_REQUIRED || ex.getErrorCode() == ErrorCode.SUBSCRIPTION_EXPIRED)) {
        return ResponseEntity.ok(Map.of("allowed", false, "billingAllowed", true));
      }
      throw ex;
    }

    return ResponseEntity.ok(Map.of("allowed", true));
  }
}
