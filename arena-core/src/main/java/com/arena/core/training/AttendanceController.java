package com.arena.core.training;

import static com.arena.core.training.AttendanceContracts.*;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/academy") @RequiredArgsConstructor
public class AttendanceController {
  private final AttendanceService service;

  @io.swagger.v3.oas.annotations.Operation(summary = "Issue or replace a student QR credential", description = "Owner or Manager only. Returns a random opaque credential once; it contains no student details and must be stored by the client. Reissuing revokes the previous credential.")
  @PostMapping("/students/{studentId}/attendance/qr") @ResponseStatus(HttpStatus.CREATED)
  public Credential issueQr(@AuthenticationPrincipal Jwt jwt, @PathVariable Long studentId) { return service.issueQr(jwt, studentId); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Register or replace an NFC/RFID tag", description = "Owner or Manager only. Accepts a hardware-independent tag identifier; the identifier is stored as a hash and is not returned by the API.")
  @PostMapping("/students/{studentId}/attendance/nfc/{tagId}") @ResponseStatus(HttpStatus.CREATED)
  public Credential registerNfc(@AuthenticationPrincipal Jwt jwt, @PathVariable Long studentId, @PathVariable String tagId) { return service.registerNfc(jwt, studentId, tagId); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Revoke an attendance credential", description = "Owner or Manager only. Credential identifiers are resolved within the authenticated academy.")
  @PostMapping("/attendance/credentials/{id}/revoke")
  public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { service.revoke(jwt, id); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Capture QR attendance", description = "The opaque QR credential must belong to the current academy and identify a student actively enrolled in the concrete training session. The first valid capture is provisional; repeated captures return the existing record.")
  @PostMapping("/attendance/qr")
  public AttendanceView qr(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Capture request) { return service.captureQr(jwt, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Capture simulated NFC/RFID attendance", description = "Accepts a raw tag identifier from an adapter, validates its stored hash and academy, then creates provisional attendance for the enrolled student and concrete session. No physical reader is implied.")
  @PostMapping("/attendance/nfc")
  public AttendanceView nfc(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Capture request) { return service.captureNfc(jwt, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Record manual attendance", description = "Owner, Manager, or assigned Instructor only. Student must have an active enrollment in the selected session batch; status must be PRESENT, ABSENT, or LATE.")
  @PostMapping("/attendance/manual")
  public AttendanceView manual(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Manual request) { return service.manual(jwt, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "Confirm provisional attendance", description = "Owner, Manager, or the Instructor assigned to the session batch. A provisional check-in is finalized as PRESENT, ABSENT, or LATE and records the confirming user and time.")
  @PostMapping("/attendance/{id}/confirm")
  public AttendanceView confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody Confirm request) { return service.confirm(jwt, id, request); }

  @io.swagger.v3.oas.annotations.Operation(summary = "List attendance for a training session", description = "Owner, Manager, or Instructor assigned to the session batch. Session and student details are academy-scoped.")
  @GetMapping("/training-sessions/{sessionId}/attendance")
  public List<AttendanceView> session(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) { return service.sessionAttendance(jwt, sessionId); }

  @io.swagger.v3.oas.annotations.Operation(summary="List enrolled students for a training session",description="Owner, Manager, or Instructor assigned to the session batch. Only active enrollment records covering the session date are returned.")
  @GetMapping("/training-sessions/{sessionId}/students")
  public List<SessionStudent> sessionStudents(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) { return service.sessionStudents(jwt, sessionId); }

  @io.swagger.v3.oas.annotations.Operation(summary = "List attendance history for a student", description = "Owner or Manager only. Student identifiers are scoped to the current academy.")
  @GetMapping("/students/{studentId}/attendance")
  public List<AttendanceView> student(@AuthenticationPrincipal Jwt jwt, @PathVariable Long studentId) { return service.studentAttendance(jwt, studentId); }
}