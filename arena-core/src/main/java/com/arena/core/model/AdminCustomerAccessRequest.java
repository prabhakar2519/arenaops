package com.arena.core.model;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AdminCustomerAccessRequest {

  @NotNull
  private Boolean accessAllowed;

  private LocalDate trialEndsAt;

  private String paymentStatus;

  private LocalDate nextDueDate;

  private String notes;
}
