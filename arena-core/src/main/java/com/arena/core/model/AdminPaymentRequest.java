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

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String currency;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String paymentMethod;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String paymentReference;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private LocalDateTime paymentDate;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String notes;
}
