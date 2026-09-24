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

  private String parlourName;

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

  private String subscriptionType;

  @NotNull
  @DecimalMin("0.00")
  private BigDecimal billingAmount;

  private String billingCurrency;

  private String notes;
}
