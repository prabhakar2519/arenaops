package com.arena.core.controller;

import com.arena.core.model.InvitationValidationRequest;
import com.arena.core.model.InvitationValidationResponse;
import com.arena.core.model.AppUserRequest;
import com.arena.core.model.AppUserResponse;
import com.arena.core.service.AdminCustomerService;
import com.arena.core.service.AppUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name="Owner registration")
@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

  private final AdminCustomerService adminCustomerService;
  private final AppUserService appUserService;

  @io.swagger.v3.oas.annotations.Operation(summary="Validate an Owner activation invitation", description="Public invitation flow. Keycloak user provisioning is server-side; no password is returned. Owner invitation consumption is separate from staff Keycloak required actions.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.INVITATION_ALREADY_CONSUMED,com.arena.core.exception.ErrorCode.INVITATION_EMAIL_MISMATCH,com.arena.core.exception.ErrorCode.INVITATION_EXPIRED,com.arena.core.exception.ErrorCode.INVITATION_INVALID,com.arena.core.exception.ErrorCode.INVITATION_NOT_READY,com.arena.core.exception.ErrorCode.INVITATION_REVOKED})
 @PostMapping("/validate-invitation")
  public ResponseEntity<InvitationValidationResponse> validateInvitation(
      @Valid @RequestBody InvitationValidationRequest request) {
    return ResponseEntity.ok(adminCustomerService.validateInvitation(request.getActivationCode(), request.getEmail()));
  }

  @io.swagger.v3.oas.annotations.Operation(summary="Activate an invited Owner account", description="Public invitation flow. Keycloak user provisioning is server-side; no password is returned. Owner invitation consumption is separate from staff Keycloak required actions.")
 @com.arena.core.documentation.DocumentedErrors({com.arena.core.exception.ErrorCode.IDENTITY_CREATION_FAILED,com.arena.core.exception.ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,com.arena.core.exception.ErrorCode.INVITATION_ALREADY_CONSUMED,com.arena.core.exception.ErrorCode.INVITATION_EMAIL_MISMATCH,com.arena.core.exception.ErrorCode.INVITATION_EXPIRED,com.arena.core.exception.ErrorCode.INVITATION_INVALID,com.arena.core.exception.ErrorCode.INVITATION_NOT_READY,com.arena.core.exception.ErrorCode.INVITATION_REVOKED,com.arena.core.exception.ErrorCode.USER_ALREADY_EXISTS})
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201", description="Successful operation")
 @PostMapping("/activate")
  public ResponseEntity<AppUserResponse> activate(@Valid @RequestBody AppUserRequest request) {
    request.setRole("OWNER");
    return ResponseEntity.status(201).body(appUserService.save(request));
  }
}
