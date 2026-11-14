package com.arena.core.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InvitationValidationRequest {

  @NotBlank
  @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY,description="Synthetic example only; never persist or log this value in documentation")
  private String activationCode;

  @NotBlank
  @Email
  private String email;
}
