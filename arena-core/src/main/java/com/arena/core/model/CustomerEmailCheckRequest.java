package com.arena.core.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerEmailCheckRequest(@NotBlank @Email String email) {}
