package com.arena.core.controller;

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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCustomerController {

  private final AdminCustomerService adminCustomerService;
  private final AdminAuthorizationService adminAuthorizationService;

  @GetMapping("/dashboard")
  public ResponseEntity<AdminDashboardResponse> dashboard(@AuthenticationPrincipal Jwt jwt) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.getDashboard());
  }

  @GetMapping("/customers")
  public ResponseEntity<List<AdminCustomerResponse>> customers(@AuthenticationPrincipal Jwt jwt) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.listCustomers());
  }

  @PostMapping("/customers")
  public ResponseEntity<AdminCustomerResponse> createCustomer(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminCustomerRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminCustomerService.createCustomer(request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @PostMapping("/customers/invitations")
  public ResponseEntity<AdminCustomerResponse> createInvitation(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminCustomerRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminCustomerService.createCustomer(request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @PostMapping("/customers/{customerId}/invitations/resend")
  public ResponseEntity<AdminCustomerResponse> resendInvitation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.resendInvitation(customerId, adminAuthorizationService.resolveUsername(jwt)));
  }

  @PostMapping("/invitations/{invitationId}/revoke")
  public ResponseEntity<AdminCustomerResponse> revokeInvitation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long invitationId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.revokeInvitation(
        invitationId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

  @PostMapping("/customers/{customerId}/grace-period")
  public ResponseEntity<AdminCustomerResponse> grantGracePeriod(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @Valid @RequestBody AdminGracePeriodRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.grantGracePeriod(
        customerId, request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @PostMapping("/customers/{customerId}/payments")
  public ResponseEntity<AdminCustomerResponse> recordPayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @Valid @RequestBody AdminPaymentRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.recordPayment(
        customerId, request, adminAuthorizationService.resolveUsername(jwt)));
  }

  @PostMapping("/customers/{customerId}/suspend")
  public ResponseEntity<AdminCustomerResponse> suspend(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.suspendCustomer(
        customerId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

  @PostMapping("/customers/{customerId}/reactivate")
  public ResponseEntity<AdminCustomerResponse> reactivate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long customerId,
      @RequestBody(required = false) AdminStateChangeRequest request) {
    assertAdmin(jwt);
    return ResponseEntity.ok(adminCustomerService.reactivateCustomer(
        customerId, adminAuthorizationService.resolveUsername(jwt), request != null ? request.getReason() : null));
  }

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
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
    }
  }
}
