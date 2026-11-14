package com.arena.core.sessions;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import jakarta.validation.constraints.*;
public final class SessionCheckoutContracts {
 private SessionCheckoutContracts(){}
 public record Authority(boolean discountEnabled,boolean canApprove,boolean canViewFinancials,BigDecimal maxPercent,BigDecimal maxAmount,BigDecimal dailyRemaining){}
 public record Quote(String token,Long sessionId,LocalDateTime capturedAt,LocalDateTime expiresAt,long durationSeconds,long totalPausedSeconds,BigDecimal originalBill,Authority authority){}
 public record QuoteToken(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String token){}
 public record Policy(@NotNull Long version,boolean enabled,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2)BigDecimal maxPercent,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2)BigDecimal maxAmount,@DecimalMin("0") @Digits(integer=10,fraction=2)BigDecimal dailyAllowance,boolean canApprove){}
 public record Discount(@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2)BigDecimal amount,@NotBlank @Size(max=500)String reason){}
 public record Decision(boolean approve,@NotBlank @Size(max=500)String reason){}
 public record Inbox(boolean canApprove,List<SessionDiscountRequest> requests){}
 public record Collection(Long sessionId,Long sportId,String sport,BigDecimal amount,LocalDate paymentDate,LocalDateTime recordedAt,String recordedBy,String reference){}
}
