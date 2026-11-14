package com.arena.core.students;

import static com.arena.core.students.StudentContracts.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academy/students")
@RequiredArgsConstructor
public class AcademyStudentController {
  private final AcademyStudentService service;

  @io.swagger.v3.oas.annotations.Operation(summary = "Search academy students", description = "Owner or Manager only. Search is case-insensitive across student and guardian names, code, email, and phone. Results are scoped to the current academy; status is ACTIVE or INACTIVE; pagination is zero-based with size 1..100.")
  @GetMapping
  public Page<Response> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(jwt, search, status, page, size);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "Read an academy student profile", description = "Owner or Manager only. Student identifiers are resolved within the authenticated academy; guardian and contact fields are returned only to authorized academy managers.")
  @GetMapping("/{id}")
  public Response get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return service.get(jwt, id);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "Register an academy student", description = "Owner or Manager only. Student email and phone are optional, including for minors. sportIds must reference active sports registered to this academy. A unique academy-scoped student code is assigned by the service.")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Response create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request request) {
    return service.create(jwt, request);
  }

  @io.swagger.v3.oas.annotations.Operation(summary = "Update an academy student", description = "Owner or Manager only. Student identifiers and sport IDs are tenant-scoped; the version field is required and stale updates return 409.")
  @PutMapping("/{id}")
  public Response update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody Request request) {
    return service.update(jwt, id, request);
  }
}