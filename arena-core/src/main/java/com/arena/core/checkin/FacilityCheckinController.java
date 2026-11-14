package com.arena.core.checkin;
import static com.arena.core.checkin.FacilityCheckinContracts.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequiredArgsConstructor
public class FacilityCheckinController {
 private final PermanentCheckinService service;
 private final com.arena.core.publicclub.PublicClubHost hosts;
 @Operation(summary="Read or create permanent facility QR",description="Authorized facility operator only. Returns the same permanent revocable public identifier and validated club-host URL for the facility. Website publication is not required; shared-host URLs resolve the venue only from the QR identifier; no session or reservation is created.")
 @PostMapping("/api/academy/facilities/{id}/check-in-qr") public ResponseEntity<Challenge> issue(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(jwt,id));}
 @Operation(summary="Read waiting facility customer check-ins",description="Authenticated tenant/sport-authorized operators only. At most 100 unexpired pending check-ins. Mobile numbers are explicitly unverified. No facility occupancy is changed.")
 @GetMapping("/api/academy/facility-check-ins") public ResponseEntity<java.util.List<Pending>> pending(@AuthenticationPrincipal Jwt jwt){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pending(jwt));}
 @Operation(summary="Start a session from a waiting check-in",description="Atomic authorized confirmation reuses the existing locked session-start workflow, rate snapshot and reservation validation. Start time is captured only at successful backend start, never customer submission. Replays/expiry/conflicts are rejected and roll back confirmation.")
 @PostMapping("/api/academy/facility-check-ins/{id}/confirm") public Confirmation confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Confirm request){return service.confirm(jwt,id,request);}
 @Operation(summary="Cancel a waiting facility check-in",description="Tenant/sport-authorized operator only. Clears submitted personal details immediately. Does not affect sessions or reservations.")
 @PostMapping("/api/academy/facility-check-ins/{id}/cancel") public void cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){service.cancel(jwt,id);}
 @Operation(summary="Inspect a public facility check-in challenge",description="Anonymous, rate limited. Token is supplied in JSON, never the URL. Returns facility label, five-minute submission nonce and expiry; no customer, member, session, pricing or private academy data. Invalid, expired and used challenges share the same unavailable response.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.QR_CHECKIN_UNAVAILABLE,com.arena.core.exception.ErrorCode.TOO_MANY_REQUESTS})
 @PostMapping("/api/public/facility-check-ins/inspect") public ResponseEntity<PublicInfo> inspect(@Valid @RequestBody Token token,jakarta.servlet.http.HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(service.inspect(hosts.verifiedCheckinSlug(request),token.token()));}
 @Operation(summary="Submit a public customer check-in",description="Anonymous, rate limited, permanent QR with single-use form nonce. Requires name, international mobile and affirmative privacy acknowledgement. Mobile is unverified; never matches or creates a training member/account. Creates a two-minute pending check-in only; one actionable pending per facility, durable mobile/facility rolling limits and explicit idempotent retries. Returns status/expiry, never personal details or private identifiers.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.QR_CHECKIN_UNAVAILABLE,com.arena.core.exception.ErrorCode.TOO_MANY_REQUESTS})
 @PostMapping("/api/public/facility-check-ins/submit") public ResponseEntity<Receipt> submit(@Valid @RequestBody Submission request,jakarta.servlet.http.HttpServletRequest servlet){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(service.submit(hosts.verifiedCheckinSlug(servlet),request));}
 @Operation(summary="Revoke permanent facility QR",description="Owner only. Existing sessions/reservations are unaffected.") @PostMapping("/api/academy/facilities/{id}/check-in-qr/revoke")public void revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){service.revoke(jwt,id);}
 @Operation(summary="Regenerate permanent facility QR",description="Owner only. Old printed identifiers stop accepting submissions.") @PostMapping("/api/academy/facilities/{id}/check-in-qr/regenerate")public Challenge regenerate(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return service.regenerate(jwt,id);}
 @Operation(summary="Clear facility QR cooldown",description="Owner or Full Operations Manager. Resets the facility acceptance window; per-mobile limits remain in force.") @PostMapping("/api/academy/facilities/{id}/check-in-qr/clear-cooldown")public void clear(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){service.clearCooldown(jwt,id);}

}
