package com.arena.core.entity;

import com.arena.core.enums.AccessStatus;
import com.arena.core.enums.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CUSTOMER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity {

  // Optimistic version protects billing activation from stale lifecycle/admin writes.
  @jakarta.persistence.Version
  @Column(name = "VERSION", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
  @SequenceGenerator(name = "customer_seq", sequenceName = "seq_customer", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "CUSTOMER_NAME", nullable = false, length = 255)
  private String customerName;

  @Column(name = "ORGANIZATION_NAME", length = 255)
  private String organizationName;

  @Column(name = "ORGANIZATION_TYPE", length = 80)
  private String organizationType;

  @Column(name = "PARLOUR_NAME", nullable = false, length = 255)
  private String parlourName;

  @Column(name = "OWNER_NAME", length = 255)
  private String ownerName;

  @Column(name = "PRIMARY_CONTACT_NAME", length = 255)
  private String primaryContactName;

  @Column(name = "OWNER_EMAIL", length = 255)
  private String ownerEmail;

  @Column(name = "PRIMARY_CONTACT_EMAIL", length = 255)
  private String primaryContactEmail;

  @Column(name = "OWNER_PHONE", length = 50)
  private String ownerPhone;

  @Column(name = "PRIMARY_CONTACT_PHONE", length = 50)
  private String primaryContactPhone;

  @Column(name = "SPORTS", nullable = false, length = 120)
  private String sports;

  @Column(name = "ONBOARDING_DATE", nullable = false)
  private LocalDate onboardingDate;

  @Column(name = "TRIAL_STARTS_AT", nullable = false)
  private LocalDate trialStartsAt;

  @Column(name = "TRIAL_ENDS_AT", nullable = false)
  private LocalDate trialEndsAt;

  @Column(name = "SUBSCRIPTION_TYPE", nullable = false, length = 30)
  private String subscriptionType;

  @Column(name = "BILLING_AMOUNT", nullable = false, precision = 12, scale = 2)
  private BigDecimal billingAmount;

  @Column(name = "BILLING_CURRENCY", nullable = false, length = 10)
  @Builder.Default
  private String billingCurrency = "INR";

  @Column(name = "BILLING_START_DATE", nullable = false)
  private LocalDate billingStartDate;

  @Column(name = "NEXT_DUE_DATE", nullable = false)
  private LocalDate nextDueDate;

  @Column(name = "PAYMENT_STATUS", nullable = false, length = 30)
  private String paymentStatus;

  @Column(name = "STATUS", nullable = false, length = 40)
  private String status;

  @Enumerated(EnumType.STRING)
  @Column(name = "CUSTOMER_STATUS", length = 30)
  private CustomerStatus customerStatus;

  @Column(name = "ACCESS_ALLOWED", nullable = false)
  @Builder.Default
  private Boolean accessAllowed = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "ACCESS_STATUS", length = 30)
  private AccessStatus accessStatus;

  @Column(name = "NOTES", length = 1000)
  private String notes;

  @Column(name = "CREATED_BY", length = 100)
  private String createdBy;

  @Column(name = "PAYMENT_ACTIVATED_BY", length = 100)
  private String paymentActivatedBy;

  @Column(name = "PAYMENT_ACTIVATED_AT")
  private LocalDateTime paymentActivatedAt;

  @Column(name = "ACCESS_UPDATED_BY", length = 100)
  private String accessUpdatedBy;

  @Column(name = "ACCESS_UPDATED_AT")
  private LocalDateTime accessUpdatedAt;

  @Column(name = "ACTIVATED_AT")
  private LocalDateTime activatedAt;

  @Column(name = "DEACTIVATED_AT")
  private LocalDateTime deactivatedAt;

  @Column(name = "DEACTIVATION_REASON", length = 500)
  private String deactivationReason;

  @Column(name = "SUSPENDED_BY", length = 100)
  private String suspendedBy;

  @Column(name = "SUSPENDED_AT")
  private LocalDateTime suspendedAt;

  @Column(name = "SUSPENSION_REASON", length = 500)
  private String suspensionReason;

  @Column(name = "CREATED_AT")
  private LocalDateTime createdAt;

  @Column(name = "UPDATED_AT")
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
