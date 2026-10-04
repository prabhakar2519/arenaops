package com.arena.core.entity;

import com.arena.core.enums.SubscriptionStatus;
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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CUSTOMER_SUBSCRIPTION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSubscriptionEntity {

  // Optimistic version protects billing activation from stale lifecycle/admin writes.
  @jakarta.persistence.Version
  @Column(name = "VERSION", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_subscription_seq")
  @SequenceGenerator(name = "customer_subscription_seq", sequenceName = "seq_customer_subscription", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "CUSTOMER_ID", nullable = false)
  private Long customerId;

  @Column(name = "BILLING_PLAN_ID", length = 40)
  private String billingPlanId;

  @Column(name = "PLAN", nullable = false, length = 40)
  private String plan;

  @Enumerated(EnumType.STRING)
  @Column(name = "SUBSCRIPTION_STATUS", nullable = false, length = 30)
  private SubscriptionStatus subscriptionStatus;

  @Column(name = "TRIAL_DURATION_DAYS", nullable = false)
  private Integer trialDurationDays;

  @Column(name = "TRIAL_STARTED_AT")
  private LocalDateTime trialStartedAt;

  @Column(name = "TRIAL_ENDS_AT")
  private LocalDateTime trialEndsAt;

  @Column(name = "SUBSCRIPTION_STARTED_AT")
  private LocalDateTime subscriptionStartedAt;

  @Column(name = "CURRENT_PERIOD_START")
  private LocalDateTime currentPeriodStart;

  @Column(name = "CURRENT_PERIOD_END")
  private LocalDateTime currentPeriodEnd;

  @Column(name = "NEXT_BILLING_DATE")
  private LocalDateTime nextBillingDate;

  @Column(name = "CANCELLED_AT")
  private LocalDateTime cancelledAt;

  @Column(name = "CANCELLATION_REASON", length = 500)
  private String cancellationReason;

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
