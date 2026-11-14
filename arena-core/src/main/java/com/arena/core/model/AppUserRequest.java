package com.arena.core.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserRequest {

  @NotBlank(message = "Username is required")
  private String username;

  @NotBlank(message = "Password is required")
  @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY,description="Synthetic example only; never persist or log this value in documentation")
  private String password;

  @NotBlank(message = "Email is required")
  @Email(message = "Valid email is required")
  private String email;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String name;

  @NotBlank(message = "Role is required")
  private String role;

  @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY,requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,description="Required by invitation validation; synthetic examples are never usable activation credentials")
  private String onboardingCode;

  @Builder.Default
  @io.swagger.v3.oas.annotations.media.Schema(types={"boolean","null"})
  private Boolean isActive = true;
}
