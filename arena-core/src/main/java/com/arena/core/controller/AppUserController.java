package com.arena.core.controller;

import com.arena.core.model.AppUserRequest;
import com.arena.core.model.AppUserResponse;
import com.arena.core.service.AppUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name="Owner registration")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class AppUserController {

  private final AppUserService appUserService;

  @io.swagger.v3.oas.annotations.Operation(summary="Register an invited Owner (legacy alias)", description="Public legacy owner registration. Requires an eligible, unexpired, unconsumed invitation matching the supplied email. Tenant/role are resolved server-side; activation is consumed transactionally.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.ACCESS_DENIED,com.arena.core.exception.ErrorCode.IDENTITY_CREATION_FAILED,com.arena.core.exception.ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,com.arena.core.exception.ErrorCode.INVITATION_ALREADY_CONSUMED,com.arena.core.exception.ErrorCode.INVITATION_EMAIL_MISMATCH,com.arena.core.exception.ErrorCode.INVITATION_EXPIRED,com.arena.core.exception.ErrorCode.INVITATION_INVALID,com.arena.core.exception.ErrorCode.INVITATION_NOT_READY,com.arena.core.exception.ErrorCode.INVITATION_REVOKED,com.arena.core.exception.ErrorCode.USER_ALREADY_EXISTS})
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="Successful operation")
 @PostMapping
  public ResponseEntity<AppUserResponse> saveUser(@Valid @RequestBody AppUserRequest request) {
    log.info("[AppUserController] POST /api/users - username={}, email={}, role={}",
        request.getUsername(), request.getEmail(), request.getRole());
    return ResponseEntity.status(HttpStatus.CREATED).body(appUserService.save(request));
  }
}
