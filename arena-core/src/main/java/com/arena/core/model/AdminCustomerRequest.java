package com.arena.core.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class AdminCustomerRequest {

  @NotBlank
  private String customerName;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String parlourName;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String organizationType;

  @NotBlank
  private String ownerName;

  @NotBlank
  @Email
  private String ownerEmail;

  @NotBlank
  private String ownerPhone;

  @NotNull
  private List<String> sports;

  @NotNull
  private Integer trialDays;

  @NotNull
  private Integer invitationExpiryDays;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String subscriptionType;

  @NotNull
  @DecimalMin("0.00")
  private BigDecimal billingAmount;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String billingCurrency;

  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})

  private String notes;
}
