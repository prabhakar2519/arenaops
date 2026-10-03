package com.arena.core.billing;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.arena.core.billing.BillingTypes.*;

/** Authenticated owner billing API; tenancy and all transitions are enforced by the application service. */
@RestController @RequestMapping("/api/billing") @RequiredArgsConstructor
public class BillingController {
  private final BillingService billing;
  /** Returns current entitlement and available authoritative prices. */
  @GetMapping public Overview overview(@AuthenticationPrincipal Jwt jwt) { return billing.overview(jwt); }
  /** Creates an idempotent order from a plan selection without accepting an amount or tenant. */
  @PostMapping("/orders") public Order create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrder request) {
    return billing.create(jwt,request);
  }
  /** Restores a tenant-scoped order after refresh. */
  @GetMapping("/orders/{id}") public Order get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return billing.get(jwt,id); }
  /** Initiates or resumes the order's payment session. */
  @PostMapping("/orders/{id}/checkout") public Order checkout(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return billing.checkout(jwt,id);
  }
  /** Abandons an unpaid order before checkout or after failure. */
  @PostMapping("/orders/{id}/cancel") public Order cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return billing.cancel(jwt,id);
  }
  /** Simulates a verified provider result only when the guarded development adapter is active. */
  @PostMapping("/orders/{id}/mock") public Order mock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
      @Valid @RequestBody MockResult request) { return billing.mock(jwt,id,request); }
}
