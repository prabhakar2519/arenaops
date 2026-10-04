package com.arena.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminCustomerResponse {
  private EmailDelivery emailDelivery;
  private Long id;
  private String organizationName;
  private String organizationType;
  private String primaryContactName;
  private String primaryContactEmail;
  private String primaryContactPhone;
  private String customerName;
  private String parlourName;
  private String ownerName;
  private String ownerEmail;
  private String ownerPhone;
  private List<String> sports;
  private LocalDate onboardingDate;
  private LocalDate trialStartsAt;
  private LocalDate trialEndsAt;
  private String subscriptionType;
  private BigDecimal billingAmount;
  private String billingCurrency;
  private LocalDate billingStartDate;
  private LocalDate nextDueDate;
  private Integer trialDurationDays;
  private LocalDateTime trialStartedAt;
  private LocalDateTime trialEndsAtDateTime;
  private LocalDateTime nextBillingDate;
  private String customerStatus;
  private String invitationStatus;
  private String subscriptionStatus;
  private String accessStatus;
  private String graceStatus;
  private LocalDateTime graceEndsAt;
  private String paymentStatus;
  private String status;
  private Boolean accessAllowed;
  private Boolean trialExpired;
  private Boolean usable;
  private String notes;
  private String onboardingCode;
  private Long invitationId;
  private String onboardingCodeStatus;
  private LocalDateTime onboardingCodeExpiresAt;
  private Long subscriptionId;
  private String paymentActivatedBy;
  private LocalDateTime paymentActivatedAt;
  private String accessUpdatedBy;
  private LocalDateTime accessUpdatedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
