package com.arena.core.sessions;
import static com.arena.core.sessions.SessionCheckoutContracts.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController @RequiredArgsConstructor
public class SessionCheckoutController {
 private final SessionCheckoutService checkout;
 @io.swagger.v3.oas.annotations.Operation(summary="Read checkout authority",description="Current tenant membership and explicit discount limits, remaining academy-local daily allowance, approval authority, and financial-report visibility. Staff discount authority defaults off.")
 @GetMapping("/api/academy/running-sessions/authority") public Authority authority(@AuthenticationPrincipal Jwt jwt){return checkout.capabilities(jwt);}
 @io.swagger.v3.oas.annotations.Operation(summary="Configure staff discount authority",description="Owner only, tenant-scoped membership version required. Percentage, per-session amount and optional daily limits all apply. Approval permission is only grantable to Full Operations Managers.")
 @PutMapping("/api/academy/staff/{id}/discount-policy") public com.arena.core.training.AcademyStaff policy(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Policy p){return checkout.policy(jwt,id,p);}
 @io.swagger.v3.oas.annotations.Operation(summary="List discount requests",description="Authorized approvers see at most 100 PENDING requests, or latest 100 with status=ALL. Other operators see only their own latest requests. Unapproved amounts are not deducted.")
 @GetMapping("/api/academy/running-sessions/discount-requests") public Inbox inbox(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="PENDING")String status){return checkout.inbox(jwt,status);}
 @io.swagger.v3.oas.annotations.Operation(summary="Read discount audit details",description="Tenant-scoped request accessible to its requester or an authorized approver, including policy snapshot, reason and decision identity and timestamps.")
 @GetMapping("/api/academy/running-sessions/discount-requests/{id}") public SessionDiscountRequest details(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return checkout.details(jwt,id);}
 @io.swagger.v3.oas.annotations.Operation(summary="Approve or reject a discount request",description="Owner or explicitly authorized Full Operations Manager only. No self approval. Locks request and session, rejects repeated decisions, records audit identity and reason. Approval reduces payable; rejection retains debt.")
 @PostMapping("/api/academy/running-sessions/discount-requests/{id}/decision") public SessionDiscountRequest decide(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Decision d){return checkout.decide(jwt,id,d);}
 @io.swagger.v3.oas.annotations.Operation(summary="Cancel a pending discount request",description="Requester or authorized approver only, within current tenant. Cancels a pending request without applying any discount or changing payment amounts.")
 @PostMapping("/api/academy/running-sessions/discount-requests/{id}/cancel") public SessionDiscountRequest cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return checkout.cancelRequest(jwt,id);}
 @io.swagger.v3.oas.annotations.Operation(summary="Apply or request a checkout discount",description="Permitted session operator only, on a closed Snooker session. Within all configured caps applies automatically; otherwise creates a pending request. Requires reason, prevents duplicate application and discounts exceeding uncollected balance.")
 @PostMapping("/api/academy/running-sessions/{id}/discount-requests") public void request(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Discount d){checkout.request(jwt,id,d);}
 @io.swagger.v3.oas.annotations.Operation(summary="Read dated session collections",description="Owner or Full Operations Manager only. Tenant-scoped payment ledger events on actual academy-local payment dates within inclusive from/to, at most 366 days apart. Imported legacy opening balances are explicitly marked; these dates are estimates, not reconstructed payment history.")
 @GetMapping("/api/academy/running-sessions/collections") public java.util.List<Collection> collections(@AuthenticationPrincipal Jwt jwt,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate from,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)java.time.LocalDate to){return checkout.collections(jwt,from,to);}
}
