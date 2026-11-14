package com.arena.core.model;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AdminCustomerAccessRequest {

  @NotNull
  private Boolean accessAllowed;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private LocalDate trialEndsAt;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String paymentStatus;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private LocalDate nextDueDate;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String notes;
}
