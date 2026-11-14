package com.arena.core.billing;

import static com.arena.core.billing.AcademyBillingContracts.*;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/academy/collections") @RequiredArgsConstructor
public class AcademyBillingController {
  private final AcademyBillingService service;

  @io.swagger.v3.oas.annotations.Operation(summary = "Assign a student fee charge", description = "Owner or Manager only. Academy charges are operational billing and are separate from ArenaOps subscription billing. A batch-specific charge requires an active enrollment in that batch.")
  @PostMapping("/charges") @ResponseStatus(HttpStatus.CREATED)
  public ChargeView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChargeRequest request) { return service.createCharge(jwt, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Assign a fee to active batch enrollments", description = "Owner or Manager only. Creates one charge per currently active enrollment in the academy-scoped batch.")
  @PostMapping("/batches/{batchId}/charges") @ResponseStatus(HttpStatus.CREATED)
  public List<ChargeView> batch(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId, @Valid @RequestBody BatchChargeRequest request) { return service.chargeActiveBatch(jwt, batchId, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "List academy fee charges", description = "Owner or Manager only. Optional studentId and batchId filters are validated within the authenticated academy.")
  @GetMapping("/charges")
  public List<ChargeView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long studentId, @RequestParam(required = false) Long batchId) { return service.list(jwt, studentId, batchId); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Record a manual payment", description = "Owner or Manager only. This records a payment reported by staff; it does not initiate a payment gateway transaction. Partial amounts are allowed, and a row lock prevents over-allocation.")
  @PostMapping("/charges/{chargeId}/payments") @ResponseStatus(HttpStatus.CREATED)
  public PaymentView payment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long chargeId, @Valid @RequestBody PaymentRequest request) { return service.recordPayment(jwt, chargeId, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "List student payment history", description = "Owner or Manager only. Payment history is scoped to the authenticated academy.")
  @GetMapping("/students/{studentId}/payments")
  public List<PaymentView> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long studentId) { return service.paymentHistory(jwt, studentId); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Read academy collection totals", description = "Owner or Manager only. Returns fee charges, allocated collections, outstanding balance, and overdue charge count for the current academy.")
  @GetMapping("/summary")
  public Summary summary(@AuthenticationPrincipal Jwt jwt) { return service.summary(jwt); }
}