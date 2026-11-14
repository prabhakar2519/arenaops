package com.arena.core.training;

import static com.arena.core.training.TrainingOperationsContracts.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/academy")
@RequiredArgsConstructor
public class TrainingOperationsController {
  private final TrainingOperationsService service;

  @io.swagger.v3.oas.annotations.Operation(summary = "Enroll a student in a training batch", description = "Owner or Manager only. Student and batch must belong to the current academy, the student must be active and registered for the batch sport, and active batch capacity and duplicate-enrollment rules are enforced.")
  @PostMapping("/enrollments")
  @ResponseStatus(HttpStatus.CREATED)
  public EnrollmentView enroll(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Enroll request) {
    return service.enroll(jwt, request);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "List a batch's enrollment history", description = "Owner or Manager only. Batch identifiers are scoped to the authenticated academy.")
  @GetMapping("/batches/{batchId}/enrollments")
  public List<EnrollmentView> batchEnrollments(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
    return service.batchEnrollments(jwt, batchId);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "List a student's enrollment history", description = "Owner or Manager only. Student and batch details are scoped to the authenticated academy.")
  @GetMapping("/students/{studentId}/enrollments")
  public List<EnrollmentView> studentEnrollments(@AuthenticationPrincipal Jwt jwt, @PathVariable Long studentId) {
    return service.studentEnrollments(jwt, studentId);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "End a student enrollment", description = "Owner or Manager only. The enrollment is retained as history and cannot be ended before its start date.")
  @PostMapping("/enrollments/{id}/end")
  public EnrollmentView endEnrollment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody(required = false) EndEnrollment request) {
    return service.endEnrollment(jwt, id, request);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "Resolve a scheduled training occurrence", description = "Owner or Manager, or an instructor assigned to the selected batch. An occurrence is resolved from the active recurring schedule and is idempotent for a batch, date, and start time.")
  @PostMapping("/training-sessions")
  public SessionView resolveSession(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResolveSession request) {
    return service.resolveSession(jwt, request);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "List scheduled training occurrences", description = "Owner or Manager can list the academy's occurrences; an Instructor sees only assigned batches. Omit date to use the current date.")
  @GetMapping("/training-sessions")
  public List<SessionView> sessions(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.sessions(jwt, date);
  }
}