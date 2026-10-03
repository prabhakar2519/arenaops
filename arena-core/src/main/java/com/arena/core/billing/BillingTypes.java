package com.arena.core.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

/** Immutable billing contracts; amounts are always supplied by the server. */
public final class BillingTypes {
  private BillingTypes() {}
  public enum Cycle { MONTHLY, ANNUAL }
  public enum OrderStatus { CREATED, PAYMENT_PENDING, PAID, FAILED, CANCELLED }
  public enum Outcome { PAID, FAILED, CANCELLED }
  public record Plan(String id, String name, Cycle cycle, BigDecimal baseAmount,
      BigDecimal taxAmount, BigDecimal total, String currency) {}
  public record CreateOrder(@NotBlank @Size(max=40) String planId, @NotNull Cycle cycle, @NotNull UUID requestId) {
    /** Rejects hidden tenant or amount fields rather than silently accepting manipulated requests. */
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Unexpected billing field"); }
  }
  public record MockResult(@NotNull UUID attemptId, @NotNull Outcome outcome) {
    /** Keeps the simulator contract free of payment credentials and client-supplied amounts. */
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Unexpected payment field"); }
  }
  public record Order(UUID id, String academy, String planId, String planName, Cycle cycle,
      BigDecimal baseAmount, BigDecimal taxAmount, BigDecimal total, String currency,
      OrderStatus status, LocalDateTime expiresAt, UUID attemptId, String paymentStatus) {}
  public record Overview(String academy, String status, String currentPlan, String sportsEntitlement, BigDecimal currentPrice, String currentCurrency, String billingCycle,
      String paymentStatus, LocalDateTime trialStartedAt, LocalDateTime trialEndsAt,
      long remainingTrialDays, LocalDateTime subscriptionStartedAt, LocalDateTime renewalAt,
      boolean mockEnabled, List<Plan> plans, Order pendingOrder) {}
}
