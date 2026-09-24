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

@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

  private final AdminCustomerService adminCustomerService;
  private final AppUserService appUserService;

  @PostMapping("/validate-invitation")
  public ResponseEntity<InvitationValidationResponse> validateInvitation(
      @Valid @RequestBody InvitationValidationRequest request) {
    return ResponseEntity.ok(adminCustomerService.validateInvitation(request.getActivationCode(), request.getEmail()));
  }

  @PostMapping("/activate")
  public ResponseEntity<AppUserResponse> activate(@Valid @RequestBody AppUserRequest request) {
    request.setRole("OWNER");
    return ResponseEntity.status(201).body(appUserService.save(request));
  }
}
