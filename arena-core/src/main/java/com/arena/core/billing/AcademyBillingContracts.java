package com.arena.core.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class AcademyBillingContracts {
  private AcademyBillingContracts() {}
  public record ChargeRequest(@NotNull @Positive Long studentId, Long batchId, @NotBlank @Size(max = 160) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount, @NotNull LocalDate dueDate) {}
  public record BatchChargeRequest(@NotBlank @Size(max = 160) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount, @NotNull LocalDate dueDate) {}
  public record PaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      LocalDate paymentDate, @NotBlank @jakarta.validation.constraints.Pattern(regexp = "CASH|BANK_TRANSFER|UPI|CARD|OTHER") String method,
      @Size(max = 120) String reference, @Size(max = 500) String notes) {}
  public record ChargeView(Long id, Long studentId, String studentCode, String studentName, Long batchId,
      String description, BigDecimal amount, BigDecimal collected, BigDecimal outstanding, LocalDate dueDate, String status) {}
  public record PaymentView(Long id, BigDecimal amount, LocalDate paymentDate, String method, String reference, String notes) {}
  public record Summary(BigDecimal charged, BigDecimal collected, BigDecimal outstanding, long openCharges, long overdueCharges) {}
}