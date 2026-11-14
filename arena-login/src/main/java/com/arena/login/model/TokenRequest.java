package com.arena.login.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequest {

    @NotBlank(message = "Authorization code is required")
    @ToString.Exclude
    @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY,description="Synthetic example only; never persist or log this value in documentation")
  private String code;

    @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

    private String redirectUri;

    @NotBlank(message = "Sign-in verifier is required")
    @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}", message = "Sign-in verifier is invalid")
    @ToString.Exclude
    @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY,description="Synthetic example only; never persist or log this value in documentation")
  private String codeVerifier;
}
