package com.arena.core.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminPaymentRequest {

  @NotNull
  @DecimalMin("0.01")
  private BigDecimal amount;

  private String currency;

  private String paymentMethod;

  private String paymentReference;

  private LocalDateTime paymentDate;

  private String notes;
}
