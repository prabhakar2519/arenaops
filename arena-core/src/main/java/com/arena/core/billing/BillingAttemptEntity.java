package com.arena.core.billing;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One provider attempt per checkout, retaining failed retries for audit. */
@Entity @Table(name="BILLING_ATTEMPT") @Getter @Setter @NoArgsConstructor
public class BillingAttemptEntity {
  @Id private UUID id;
  @Column(nullable=false) private UUID orderId;
  @Column(nullable=false) private String provider;
  @Column(nullable=false) private String providerReference;
  @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
  @Column(nullable=false) private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private BillingTypes.OrderStatus status;
  @Column(nullable=false) private LocalDateTime createdAt;
  private LocalDateTime completedAt;
}
