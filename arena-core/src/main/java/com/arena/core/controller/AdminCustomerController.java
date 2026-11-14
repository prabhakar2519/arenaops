package com.arena.core.controller;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;

import com.arena.core.model.CustomerEmailCheckRequest;
import com.arena.core.validation.CustomerEmailValidator;
import java.util.Map;
import com.arena.core.model.AdminCustomerRequest;
import com.arena.core.model.AdminCustomerResponse;
import com.arena.core.model.AdminDashboardResponse;
import com.arena.core.model.AdminGracePeriodRequest;
import com.arena.core.model.AdminPaymentRequest;
import com.arena.core.model.AdminStateChangeRequest;
import com.arena.core.service.AdminAuthorizationService;
import com.arena.core.service.AdminCustomerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name="Administration")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCustomerController {

  private final AdminCustomerService adminCustomerService;
  private final AdminAuthorizationService adminAuthorizationService;
  private final CustomerEmailValidator customerEmailValidator;

  @io.swagger.v3.oas.annotations.Operation(summary="Check whether a customer email is registered", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/email-check")
  public ResponseEntity<Map<String, Boolean>> checkEmail(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CustomerEmailCheckRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(Map.of("registered", customerEmailValidator.isRegistered(request.email())));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Get the platform customer dashboard", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @GetMapping("/dashboard")
  public ResponseEntity<AdminDashboardResponse> dashboard(@AuthenticationPrincipal Jwt jwt) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.getDashboard());
  }

  @io.swagger.v3.oas.annotations.Operation(summary="List platform customers", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @GetMapping("/customers")
  public ResponseEntity<List<AdminCustomerResponse>> customers(@AuthenticationPrincipal Jwt jwt) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.listCustomers());
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Create an invited customer", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_ALREADY_EXISTS,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="Successful operation")
 @PostMapping("/customers")
  public ResponseEntity<AdminCustomerResponse> createCustomer(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminCustomerRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminCustomerService.createCustomer(request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Create a customer invitation (creation alias)", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_ALREADY_EXISTS,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="Successful operation")
 @PostMapping("/customers/invitations")
  public ResponseEntity<AdminCustomerResponse> createInvitation(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminCustomerRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminCustomerService.createCustomer(request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Resend an Owner invitation", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_NOT_FOUND,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/{customerId}/invitations/resend")
  public ResponseEntity<AdminCustomerResponse> resendInvitation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.resendInvitation(customerId, adminAuthorizationService.resolveUsername(jwt)));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Revoke an Owner invitation", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CONFLICT,com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.INVITATION_NOT_FOUND,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/invitations/{invitationId}/revoke")
  public ResponseEntity<AdminCustomerResponse> revokeInvitation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long invitationId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.revokeInvitation(
        invitationId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Grant a customer grace period", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_NOT_FOUND,com.arena.core.exception.ErrorCode.GRACE_PERIOD_ALREADY_ACTIVE,com.arena.core.exception.ErrorCode.GRACE_PERIOD_NOT_ALLOWED,com.arena.core.exception.ErrorCode.INVALID_GRACE_END,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/{customerId}/grace-period")
  public ResponseEntity<AdminCustomerResponse> grantGracePeriod(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @Valid @RequestBody AdminGracePeriodRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.grantGracePeriod(
        customerId, request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Record a manual customer payment", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_NOT_FOUND,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_CANCELLED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/{customerId}/payments")
  public ResponseEntity<AdminCustomerResponse> recordPayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @Valid @RequestBody AdminPaymentRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.recordPayment(
        customerId, request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Suspend customer access", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_NOT_FOUND,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/{customerId}/suspend")
  public ResponseEntity<AdminCustomerResponse> suspend(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.suspendCustomer(
        customerId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Reactivate a customer", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_NOT_FOUND,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/{customerId}/reactivate")
  public ResponseEntity<AdminCustomerResponse> reactivate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.reactivateCustomer(
        customerId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Cancel a customer subscription", description="Platform ADMIN/PLATFORM_ADMIN only. Tenant identifiers are administrative targets. Invitation delivery failure can be returned in emailDelivery while the saved invitation remains a successful response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.CUSTOMER_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.CUSTOMER_NOT_FOUND,com.arena.core.exception.ErrorCode.PAYMENT_REQUIRED,com.arena.core.exception.ErrorCode.SERVICE_UNAVAILABLE,com.arena.core.exception.ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_EXPIRED,com.arena.core.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND,com.arena.core.exception.ErrorCode.USER_DISABLED})
 @PostMapping("/customers/{customerId}/cancel")
  public ResponseEntity<AdminCustomerResponse> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.cancelSubscription(
        customerId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

  private void assertAdmin(Jwt jwt) {
    if (!adminAuthorizationService.isAdmin(jwt)) {
      throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    }
  }
}
