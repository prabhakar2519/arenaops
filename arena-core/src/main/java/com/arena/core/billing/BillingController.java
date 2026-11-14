package com.arena.core.billing;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.arena.core.billing.BillingTypes.*;

/** Authenticated owner billing API; tenancy and all transitions are enforced by the application service. */
@io.swagger.v3.oas.annotations.tags.Tag(name="Billing")
@RestController @RequestMapping("/api/billing") @RequiredArgsConstructor
public class BillingController {
  private final BillingService billing;
  /** Returns current entitlement and available authoritative prices. */
  @io.swagger.v3.oas.annotations.Operation(summary="Get Owner billing overview and prices", description="Owner only, current tenant resolved server-side; no client-selected tenant or amount. Mock completion is available only with the guarded development adapter. No real payment-gateway integration is exposed.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @GetMapping public Overview overview(@AuthenticationPrincipal Jwt jwt) { return billing.overview(jwt); }
  /** Creates an idempotent order from a plan selection without accepting an amount or tenant. */
  @io.swagger.v3.oas.annotations.Operation(summary="Create or restore an idempotent billing order", description="Owner only, current tenant resolved server-side; no client-selected tenant or amount. Mock completion is available only with the guarded development adapter. No real payment-gateway integration is exposed. Select a server-authoritative plan and cycle using a UUID requestId. Amounts/currency/tenant are not accepted from the caller. Compatible retries reuse the existing order; mismatched requests conflict.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CONFLICT,com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.RESOURCE_NOT_FOUND,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/orders") public Order create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrder request) {
    return billing.create(jwt,request);
  }
  /** Restores a tenant-scoped order after refresh. */
  @io.swagger.v3.oas.annotations.Operation(summary="Get a tenant-scoped billing order", description="Owner only, current tenant resolved server-side; no client-selected tenant or amount. Mock completion is available only with the guarded development adapter. No real payment-gateway integration is exposed.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CONFLICT,com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.RESOURCE_NOT_FOUND,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @GetMapping("/orders/{id}") public Order get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return billing.get(jwt,id); }
  /** Initiates or resumes the order's payment session. */
  @io.swagger.v3.oas.annotations.Operation(summary="Start or resume order checkout", description="Owner only, current tenant resolved server-side; no client-selected tenant or amount. Mock completion is available only with the guarded development adapter. No real payment-gateway integration is exposed.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CONFLICT,com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.RESOURCE_NOT_FOUND,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/orders/{id}/checkout") public Order checkout(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return billing.checkout(jwt,id);
  }
  /** Abandons an unpaid order before checkout or after failure. */
  @io.swagger.v3.oas.annotations.Operation(summary="Cancel an unpaid order", description="Owner only, current tenant resolved server-side; no client-selected tenant or amount. Mock completion is available only with the guarded development adapter. No real payment-gateway integration is exposed.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CONFLICT,com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.RESOURCE_NOT_FOUND,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/orders/{id}/cancel") public Order cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return billing.cancel(jwt,id);
  }
  /** Simulates a verified provider result only when the guarded development adapter is active. */
  @io.swagger.v3.oas.annotations.Operation(summary="Simulate a payment outcome (guarded development adapter)", description="Owner only, current tenant resolved server-side; no client-selected tenant or amount. Mock completion is available only with the guarded development adapter. No real payment-gateway integration is exposed. Guarded development-only provider simulator. Uses the current attemptId and outcome; never accepts payment credentials. Disabled simulator is concealed with 404.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CONFLICT,com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.RESOURCE_NOT_FOUND,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/orders/{id}/mock") public Order mock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
      @Valid @RequestBody MockResult request) { return billing.mock(jwt,id,request); }
}
