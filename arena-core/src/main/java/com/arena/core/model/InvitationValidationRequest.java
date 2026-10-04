package com.arena.core.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InvitationValidationRequest {

  @NotBlank
  private String activationCode;

  @NotBlank
  @Email
  private String email;
}
