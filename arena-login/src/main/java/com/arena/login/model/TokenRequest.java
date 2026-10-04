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
    private String code;

    private String redirectUri;

    @NotBlank(message = "Sign-in verifier is required")
    @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}", message = "Sign-in verifier is invalid")
    @ToString.Exclude
    private String codeVerifier;
}
