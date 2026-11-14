package com.arena.core.sessions;

import static com.arena.core.sessions.RunningSessionContracts.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
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

@RestController @RequestMapping("/api/academy/running-sessions") @RequiredArgsConstructor
public class RunningSessionController {
  private final RunningSessionService service;

  @io.swagger.v3.oas.annotations.Operation(summary="Read Academy running-session dashboard",description="Active sessions, recent history, today's charges and collected totals for the authenticated academy. Session and financial records are tenant-scoped.")
  @GetMapping("/dashboard") public Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) { return service.dashboard(jwt); }

  @io.swagger.v3.oas.annotations.Operation(summary="Search facility session history",description="Paginated tenant-scoped history, filtered by academy-local dates, status and player. Defaults to the last 30 days.")
  @GetMapping("/history") public org.springframework.data.domain.Page<SessionView> history(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
      @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
      @RequestParam(required=false) String status,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) { return service.history(jwt,from,to,status,search,page,size); }

  @io.swagger.v3.oas.annotations.Operation(summary="Export authorized session history as XLSX",description="All matching tenant-scoped history rows. Dates are academy-local. Staff receive original bill and coarse settlement state only; financial roles retain detailed fields.")
  @GetMapping(value="/history/export",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  public org.springframework.http.ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
      @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
      @RequestParam(required=false) String status,@RequestParam(required=false) String search){
    return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=\"arenaops-session-history-"+java.time.LocalDate.now(java.time.ZoneOffset.UTC)+".xlsx\"").body(service.historyWorkbook(jwt,from,to,status,search));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Start a facility session",description="Owner, Manager, Instructor, Coach, or Staff with active academy membership. Facility must be active, configured, and attached to an active Snooker or Badminton activity with Play & Facility Access enabled. One running session per facility is enforced.")
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public SessionView start(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Start request) { return service.start(jwt,request); }

  @io.swagger.v3.oas.annotations.Operation(summary="Reserve an available facility", description="Reserves an eligible facility for an explicit academy-local start/end interval. Overlapping reservations or running-session windows are rejected. Adjacent intervals are allowed.")
  @PostMapping("/reserve") @ResponseStatus(HttpStatus.CREATED) public SessionView reserve(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Reserve request) { return service.reserve(jwt,request); }

  @io.swagger.v3.oas.annotations.Operation(summary="Start a reserved facility session", description="Starts a tenant-scoped hold using the current hourly rate. Reservation waiting time is never billed.")
  @PostMapping("/{id}/start-reserved") public SessionView startReserved(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.startReserved(jwt,id); }

  @io.swagger.v3.oas.annotations.Operation(summary="Cancel a facility reservation", description="Releases the tenant-scoped hold with no charge. Only reserved sessions can be cancelled this way.")
  @PostMapping("/{id}/cancel-reservation") public SessionView cancelReservation(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.cancelReservation(jwt,id); }

  @io.swagger.v3.oas.annotations.Operation(summary="Stop and calculate a facility session",description="Requires a valid unconsumed server quote; freezes end time and bill at quote capture. Stale, expired, or replayed quotes are rejected. Explicit discounts are applied only within authority, otherwise requested for approval. Partial payment never implies a discount. Client manual duration overrides are rejected. Applies the SPMS billable-duration rule excluding pauses. Sessions below one minute are cancelled unless quick-stopped. Snooker totals round down to whole units; Badminton totals use two decimals. Optional payment is a staff-recorded collection, not a gateway transaction.")
  @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.SESSION_QUOTE_STALE,com.arena.core.exception.ErrorCode.INVALID_STATE,com.arena.core.exception.ErrorCode.CONFLICT})
  @PostMapping("/{id}/stop") public SessionView stop(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Stop request) { return service.stop(jwt,id,request); }

  @io.swagger.v3.oas.annotations.Operation(summary="Capture a frozen stop quote",description="Captures server time, billable seconds excluding pauses and the existing snapshotted-rate bill. Returns an actor/tenant/session/version-bound opaque token, valid for 120 seconds by default. Does not stop or pause play; refreshing revokes previous quotes for this actor and session.")
  @PostMapping("/{id}/stop-quote") public SessionCheckoutContracts.Quote quote(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return service.stopQuote(jwt,id);}
  @io.swagger.v3.oas.annotations.Operation(summary="Discard a stop quote",description="Revokes the authenticated operator stop quote without changing session status, billing, or facility availability.")
  @PostMapping("/{id}/stop-quote/cancel") public void cancelQuote(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody SessionCheckoutContracts.QuoteToken token){service.cancelQuote(jwt,id,token.token());}

  @io.swagger.v3.oas.annotations.Operation(summary="Pause a running facility session",description="Records a pause for the authenticated academy's running session; elapsed paused time is excluded from billable duration.")
  @PostMapping("/{id}/pause") public SessionView pause(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.pause(jwt,id); }

  @io.swagger.v3.oas.annotations.Operation(summary="Resume a paused facility session",description="Closes the current pause interval and resumes the tenant-scoped session.")
  @PostMapping("/{id}/resume") public SessionView resume(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.resume(jwt,id); }

  @io.swagger.v3.oas.annotations.Operation(summary="Settle an unsettled facility session",description="Records the total collected for an unpaid stopped or UNCONFIRMED session. Partial collection remains unsettled until fully paid. This does not initiate a payment gateway transaction.")
  @PostMapping("/{id}/settle") public SessionView settle(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestParam(required=false) @DecimalMin("0") BigDecimal paidAmount) { return service.settle(jwt,id,paidAmount); }
}